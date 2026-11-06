# Grow Up — app Android

App de treino (Capacitor 8 + web app em `www/`). Cada push na branch `main` gera um APK automaticamente pelo GitHub Actions e publica em **Releases**.

## Instalar no celular
1. No celular, abra a página do repositório → **Releases** → a versão mais recente.
2. Toque em `GrowUp-v1.0.X.apk` para baixar.
3. Abra o arquivo. Se o Android pedir, permita **instalar apps desta fonte** (Chrome ou Arquivos).
4. Atualizações: baixe o APK novo e instale por cima — os dados ficam no celular.

## Levar dados da versão web
Na versão web: **Ajustes → Backup → Backup completo (com fotos)**. No app: na primeira tela, **Restaurar backup** (ou depois em Ajustes → Backup).

## Estrutura
- `www/` — o app (HTML/CSS/JS único + imagens e fontes locais)
- `android/` — projeto Android nativo gerado pelo Capacitor
- `.github/workflows/android.yml` — build do APK
- `android/app/treinohg.keystore` — chave de assinatura (mantém as atualizações instaláveis por cima). Repositório privado recomendado.
