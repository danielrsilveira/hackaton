// Nativo: API em localhost:8080. No docker compose (profile full): API_URL=http://api:8080
const target = process.env.API_URL || 'http://localhost:8080';

module.exports = {
  '/api': { target, secure: false, changeOrigin: true },
  '/mock-snp': { target, secure: false, changeOrigin: true },
};
