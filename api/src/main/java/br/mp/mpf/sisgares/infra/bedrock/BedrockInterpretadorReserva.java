package br.mp.mpf.sisgares.infra.bedrock;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.mp.mpf.sisgares.dominio.InterpretacaoReserva;
import br.mp.mpf.sisgares.dominio.ItemRecurso;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.dominio.ReservaInput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Interpreta a descrição com Amazon Bedrock (Converse API + tool use para forçar JSON estruturado).
 * Ativo só quando app.bedrock.enabled=true, para que a aplicação suba sem credenciais AWS.
 */
@Component
@ConditionalOnProperty(name = "app.bedrock.enabled", havingValue = "true")
public class BedrockInterpretadorReserva implements InterpretadorReserva {

    private static final Logger log = LoggerFactory.getLogger(BedrockInterpretadorReserva.class);
    private static final DateTimeFormatter ISO_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final BedrockRuntimeClient cliente;
    private final String modelId;
    private final ObjectMapper json = new ObjectMapper();

    public BedrockInterpretadorReserva(BedrockRuntimeClient cliente,
            @Value("${app.bedrock.model-id}") String modelId) {
        this.cliente = cliente;
        this.modelId = modelId;
    }

    @Override
    public boolean habilitado() {
        return true;
    }

    @Override
    public InterpretacaoReserva interpretar(String descricao, LocalDateTime agora, Catalogo catalogo) {
        String texto = descricao == null ? "" : descricao.strip();
        if (texto.isEmpty()) {
            throw new RegraException("IA", "Descreva a reserva em poucas frases para o preenchimento automático.");
        }
        try {
            ConverseResponse resposta = cliente.converse(req -> req
                    .modelId(modelId)
                    .system(sb -> sb.text(systemPrompt(agora, catalogo)))
                    .messages(Message.builder().role(ConversationRole.USER)
                            .content(ContentBlock.fromText(texto)).build())
                    .toolConfig(toolConfig())
                    .inferenceConfig(InferenceConfiguration.builder().maxTokens(1024).temperature(0F).build()));

            ToolUseBlock uso = extrairToolUse(resposta);
            JsonNode saida = DocumentJson.paraNode(uso.input(), json);
            return montar(saida, catalogo);
        } catch (RegraException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao interpretar reserva via Bedrock", e);
            throw new RegraException("IA",
                    "Não foi possível interpretar a descrição agora. Tente reescrever ou preencha os campos manualmente.");
        }
    }

    private String systemPrompt(LocalDateTime agora, Catalogo catalogo) {
        String dia = agora.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        StringBuilder sb = new StringBuilder();
        sb.append("Você ajuda a preencher um formulário de reserva de ambientes e recursos do MPF. ")
                .append("A partir da descrição do solicitante, extraia os campos e chame a ferramenta preencher_reserva. ")
                .append("Responda SEMPRE chamando a ferramenta, nunca com texto livre.\n\n")
                .append("Data e hora atuais: ").append(agora.format(ISO_MIN)).append(" (").append(dia).append("). ")
                .append("Resolva datas relativas (\"amanhã\", \"sexta que vem\", \"semana que vem\") a partir desse momento. ")
                .append("Fuso America/Fortaleza. Formato de data/hora: yyyy-MM-ddTHH:mm (sem segundos, sem fuso).\n\n")
                .append("Regras:\n")
                .append("- Use APENAS os ids listados abaixo. Se nada corresponder, deixe o campo nulo e registre em 'avisos'.\n")
                .append("- ambienteId nulo significa \"local próprio\"; nesse caso preencha complementoAmbiente.\n")
                .append("- qtdParticipantes é inteiro >= 1. Se não informado, estime com cautela e avise.\n")
                .append("- disposicaoId só faz sentido quando há ambienteId; caso contrário, deixe nulo.\n")
                .append("- recursos: só itens realmente pedidos; quantidade apenas para recursos limitados.\n")
                .append("- Não invente períodos: se o solicitante não deu data/hora, deixe periodos vazio e avise.\n\n");

        sb.append("AMBIENTES (id — descrição):\n");
        if (catalogo.ambientes().isEmpty()) {
            sb.append("  (nenhum)\n");
        }
        for (OpcaoAmbiente a : catalogo.ambientes()) {
            sb.append("  ").append(a.id()).append(" — ").append(a.descricao()).append('\n');
        }
        sb.append("\nDISPOSIÇÕES (id — descrição):\n");
        if (catalogo.disposicoes().isEmpty()) {
            sb.append("  (nenhuma)\n");
        }
        for (OpcaoDisposicao d : catalogo.disposicoes()) {
            sb.append("  ").append(d.id()).append(" — ").append(d.descricao()).append('\n');
        }
        sb.append("\nRECURSOS (id — descrição [grupo] limitado?):\n");
        if (catalogo.recursos().isEmpty()) {
            sb.append("  (nenhum)\n");
        }
        for (OpcaoRecurso r : catalogo.recursos()) {
            sb.append("  ").append(r.id()).append(" — ").append(r.descricao())
                    .append(" [").append(r.grupo()).append(']')
                    .append(r.limitado() ? " limitado" : "").append('\n');
        }
        return sb.toString();
    }

    /** Schema da ferramenta como Document (o SDK espera o JSON Schema como Document). */
    private ToolConfiguration toolConfig() {
        Document schema = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", Document.mapBuilder()
                        .putDocument("ambienteId", prop("integer", "Id do ambiente ou nulo para local próprio"))
                        .putDocument("complementoAmbiente", prop("string", "Local quando não há ambiente da lista"))
                        .putDocument("finalidade", prop("string", "Finalidade/objetivo do evento"))
                        .putDocument("qtdParticipantes", prop("integer", "Participantes estimados (>=1)"))
                        .putDocument("disposicaoId", prop("integer", "Id da disposição ou nulo"))
                        .putDocument("periodos", Document.mapBuilder()
                                .putString("type", "array")
                                .putDocument("items", Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", Document.mapBuilder()
                                                .putDocument("inicio", prop("string", "yyyy-MM-ddTHH:mm"))
                                                .putDocument("termino", prop("string", "yyyy-MM-ddTHH:mm"))
                                                .build())
                                        .build())
                                .build())
                        .putDocument("recursos", Document.mapBuilder()
                                .putString("type", "array")
                                .putDocument("items", Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", Document.mapBuilder()
                                                .putDocument("recursoId", prop("integer", "Id do recurso"))
                                                .putDocument("quantidade", prop("integer", "Só para recursos limitados"))
                                                .build())
                                        .build())
                                .build())
                        .putDocument("avisos", Document.mapBuilder()
                                .putString("type", "array")
                                .putString("description", "Pontos ambíguos ou não mapeados, em português")
                                .putDocument("items", Document.mapBuilder().putString("type", "string").build())
                                .build())
                        .build())
                .putDocument("required", Document.listBuilder().addString("finalidade").build())
                .build();

        ToolSpecification spec = ToolSpecification.builder()
                .name("preencher_reserva")
                .description("Preenche os campos do formulário de reserva a partir da descrição.")
                .inputSchema(ToolInputSchema.builder().json(schema).build())
                .build();

        return ToolConfiguration.builder()
                .tools(Tool.builder().toolSpec(spec).build())
                .toolChoice(ToolChoice.builder().tool(t -> t.name("preencher_reserva")).build())
                .build();
    }

    private static Document prop(String tipo, String descricao) {
        return Document.mapBuilder().putString("type", tipo).putString("description", descricao).build();
    }

    private ToolUseBlock extrairToolUse(ConverseResponse resposta) {
        if (resposta.stopReason() != StopReason.TOOL_USE || resposta.output() == null
                || resposta.output().message() == null) {
            throw new IllegalStateException("Resposta do modelo sem chamada de ferramenta");
        }
        return resposta.output().message().content().stream()
                .map(ContentBlock::toolUse).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalStateException("Bloco toolUse ausente"));
    }

    private InterpretacaoReserva montar(JsonNode n, Catalogo catalogo) {
        Long ambienteId = idValido(asLong(n.get("ambienteId")),
                catalogo.ambientes().stream().map(OpcaoAmbiente::id).toList());
        Long disposicaoId = ambienteId == null ? null
                : idValido(asLong(n.get("disposicaoId")),
                        catalogo.disposicoes().stream().map(OpcaoDisposicao::id).toList());

        List<Long> recursosValidos = catalogo.recursos().stream().map(OpcaoRecurso::id).toList();
        List<ItemRecurso> recursos = new ArrayList<>();
        if (n.has("recursos") && n.get("recursos").isArray()) {
            for (JsonNode r : n.get("recursos")) {
                Long id = idValido(asLong(r.get("recursoId")), recursosValidos);
                if (id != null) {
                    recursos.add(new ItemRecurso(id, asInt(r.get("quantidade"))));
                }
            }
        }

        List<Periodo> periodos = new ArrayList<>();
        if (n.has("periodos") && n.get("periodos").isArray()) {
            for (JsonNode p : n.get("periodos")) {
                LocalDateTime ini = asData(p.get("inicio"));
                LocalDateTime fim = asData(p.get("termino"));
                if (ini != null || fim != null) {
                    periodos.add(new Periodo(ini, fim));
                }
            }
        }

        List<String> avisos = new ArrayList<>();
        if (n.has("avisos") && n.get("avisos").isArray()) {
            n.get("avisos").forEach(a -> {
                if (a.isString() && !a.asString().isBlank()) {
                    avisos.add(a.asString().strip());
                }
            });
        }

        ReservaInput reserva = new ReservaInput(ambienteId, asTexto(n.get("complementoAmbiente")),
                asTexto(n.get("finalidade")), asInt(n.get("qtdParticipantes")), disposicaoId, periodos, recursos);

        return new InterpretacaoReserva(reserva, resumoDe(reserva, catalogo), avisos);
    }

    private String resumoDe(ReservaInput r, Catalogo catalogo) {
        List<String> partes = new ArrayList<>();
        if (r.ambienteId() != null) {
            catalogo.ambientes().stream().filter(a -> a.id() == r.ambienteId()).findFirst()
                    .ifPresent(a -> partes.add("ambiente \"" + a.descricao() + "\""));
        } else if (r.complementoAmbiente() != null && !r.complementoAmbiente().isBlank()) {
            partes.add("local \"" + r.complementoAmbiente() + "\"");
        }
        if (r.qtdParticipantes() != null) {
            partes.add(r.qtdParticipantes() + " participante(s)");
        }
        if (!r.periodos().isEmpty()) {
            partes.add(r.periodos().size() + " período(s)");
        }
        if (!r.recursos().isEmpty()) {
            partes.add(r.recursos().size() + " recurso(s)");
        }
        return partes.isEmpty() ? "Preenchi o que foi possível a partir da descrição."
                : "Entendi: " + String.join(", ", partes) + ". Confira e ajuste antes de salvar.";
    }

    // ---- Conversões tolerantes (o modelo pode omitir campos ou mandar null) ----

    private static Long idValido(Long id, List<Long> validos) {
        return id != null && validos.contains(id) ? id : null;
    }

    private static String asTexto(JsonNode n) {
        return n == null || n.isNull() || !n.isString() || n.asString().isBlank() ? null : n.asString().strip();
    }

    private static Integer asInt(JsonNode n) {
        return n == null || n.isNull() || !n.isNumber() ? null : n.asInt();
    }

    private static Long asLong(JsonNode n) {
        return n == null || n.isNull() || !n.isNumber() ? null : n.asLong();
    }

    private static LocalDateTime asData(JsonNode n) {
        if (n == null || n.isNull() || !n.isString() || n.asString().isBlank()) {
            return null;
        }
        try {
            String v = n.asString().strip();
            return LocalDateTime.parse(v.length() > 16 ? v.substring(0, 16) : v, ISO_MIN);
        } catch (Exception e) {
            return null;
        }
    }

    /** Converte o Document (árvore do SDK) em JsonNode, reusando o Jackson do projeto. */
    private static final class DocumentJson {

        static JsonNode paraNode(Document doc, ObjectMapper json) {
            if (doc == null || doc.isNull()) {
                return json.nullNode();
            }
            if (doc.isMap()) {
                var obj = json.createObjectNode();
                doc.asMap().forEach((k, v) -> obj.set(k, paraNode(v, json)));
                return obj;
            }
            if (doc.isList()) {
                var arr = json.createArrayNode();
                doc.asList().forEach(d -> arr.add(paraNode(d, json)));
                return arr;
            }
            if (doc.isBoolean()) {
                return json.getNodeFactory().booleanNode(doc.asBoolean());
            }
            if (doc.isNumber()) {
                return json.getNodeFactory().numberNode(new java.math.BigDecimal(doc.asNumber().stringValue()));
            }
            return json.getNodeFactory().textNode(doc.asString());
        }
    }
}
