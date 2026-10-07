# Infraestrutura AWS (fase 1)

Stack CloudFormation `sisgares`, definida em [`template.yml`](template.yml), na conta do profile `hackaton` (us-east-1).

```
Usuário ─▶ CloudFront ─┬─▶ S3 privado (Angular)
                       └─ /api/*, /mock-snp/* ▶ ALB ▶ ECS Fargate (Spring Boot) ▶ Aurora Serverless v2 (PostgreSQL 17)
```

- O ALB só aceita tráfego do CloudFront (prefix list gerenciada); as tarefas só aceitam o ALB; o banco só aceita as tarefas.
- A senha do banco é gerada pelo RDS e fica no Secrets Manager. A tarefa a recebe como variável de ambiente.
- Usa a VPC padrão, com tarefas em subnets públicas, para dispensar NAT Gateway. O banco não tem acesso público.
- A API roda com o profile `seed` (dados de demonstração e SNP/e-mail simulados) e `AUTH_MODO=cognito`: exige JWT do user pool da stack `sisgares-auth` ([`cognito.yaml`](cognito.yaml)). Sem o profile `local`, a API recusa o modo simulado.
- Limitações desta fase: CloudFront→ALB em HTTP (sem domínio próprio), 1 tarefa
  (o lock da RN7 é em memória) e banco Aurora Serverless v2 com uma só instância (0,5 a 2 ACU, parâmetros `DbMinCapacity` e `DbMaxCapacity`; com mínimo 0 o banco pausa e a primeira requisição espera cerca de 15 s).

Todos os comandos usam `--profile hackaton --region us-east-1` e funcionam em PowerShell, cmd e bash.
Os IDs da VPC e das subnets abaixo mudam de conta para conta (os do exemplo são de outra conta); confira com
`aws ec2 describe-subnets --filters Name=default-for-az,Values=true --profile hackaton --region us-east-1`.

## Primeiro deploy

0. Criar a stack de autenticação `sisgares-auth` (`infra/cognito.yaml`, ver README da raiz) e anotar `IssuerUri` e `ClientId`.

1. Criar a infraestrutura com a API zerada (o ECR ainda está vazio):

```
aws cloudformation deploy --stack-name sisgares --template-file infra/template.yml --capabilities CAPABILITY_IAM --parameter-overrides VpcId=vpc-0eb609adb4b302271 SubnetIds=subnet-05f071d072d0f777f,subnet-0b9d145553f2ceaba ApiDesiredCount=0 AuthIssuerUri=<IssuerUri> AuthClientId=<ClientId> --tags Project=sisgares --profile hackaton --region us-east-1
```

2. Enviar a imagem da API:

```
aws ecr get-login-password --profile hackaton --region us-east-1 | docker login --username AWS --password-stdin 068890870766.dkr.ecr.us-east-1.amazonaws.com
docker build -t 068890870766.dkr.ecr.us-east-1.amazonaws.com/sisgares-api:latest ./api
docker push 068890870766.dkr.ecr.us-east-1.amazonaws.com/sisgares-api:latest
```

3. Subir a API (repita o comando do passo 1 com `ApiDesiredCount=1`). Depois, atualize a `sisgares-auth` com `AppUrls=<saída Url>` para o CloudFront ser aceito como callback/logout do login.

4. Publicar o front (use o nome do bucket e o ID da distribuição das saídas da stack):

```
aws cloudformation describe-stacks --stack-name sisgares --query "Stacks[0].Outputs" --output table --profile hackaton --region us-east-1
cd web
npm ci
npm run build
cd ..
aws s3 sync web/dist/web/browser s3://<WebBucketName> --delete --exclude index.html --cache-control "public,max-age=31536000,immutable" --profile hackaton --region us-east-1
aws s3 cp web/dist/web/browser/index.html s3://<WebBucketName>/index.html --cache-control "no-cache" --content-type text/html --profile hackaton --region us-east-1
```

5. Publicar a configuração de autenticação do front (o S3 não gera o `config.json` como o nginx do `docker compose`). Faça **depois** do `sync`, que traz o `config.json` do modo simulado:

```
aws s3 cp config.json s3://<WebBucketName>/config.json --cache-control "no-store" --content-type application/json --profile hackaton --region us-east-1
```

Com `{"authModo":"cognito","cognitoDomain":"<saída CognitoDomain>","clientId":"<saída ClientId>"}` no arquivo local `config.json` (sem segredos).

A URL do sistema é a saída `Url` da stack.

## Atualizar

- **API:** rebuild e push da imagem (passo 2) e depois
  `aws ecs update-service --cluster sisgares --service sisgares-api --force-new-deployment --profile hackaton --region us-east-1`.
- **Front:** passos 4 e 5. Se o `index.html` estiver em cache, invalide:
  `aws cloudfront create-invalidation --distribution-id <DistributionId> --paths "/index.html" --profile hackaton --region us-east-1`.
- **Infra:** edite `template.yml` e repita o comando do passo 3.

## Remover tudo

Esvazie o bucket e apague a stack (o banco é apagado junto, sem snapshot):

```
aws s3 rm s3://<WebBucketName> --recursive --profile hackaton --region us-east-1
aws cloudformation delete-stack --stack-name sisgares --profile hackaton --region us-east-1
```
