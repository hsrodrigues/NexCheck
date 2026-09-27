<p align="center">
  <img src="docs/screenshots/00_login.png" width="220" alt="Tela de login do NexCheck">
</p>

<h1 align="center">NexCheck</h1>

<p align="center">
  Inspeções eletromecânicas e gestão de frota de caminhões, direto do celular.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Firebase" src="https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore-FFCA28?logo=firebase&logoColor=black">
  <img alt="Licença" src="https://img.shields.io/badge/licen%C3%A7a-todos%20os%20direitos%20reservados-lightgrey">
</p>

---

## Download

Baixe o APK mais recente em **[Releases](https://github.com/hsrodrigues/NexCheck/releases/latest)**. Para receber as atualizações automaticamente, adicione este repositório no [Obtainium](https://github.com/ImranR98/Obtainium).

## Sobre

O **NexCheck** é um app Android para vistoriadores e equipes de frota. Com ele, a vistoria eletromecânica de cavalos mecânicos e carretas é feita no celular: checklist item a item, assinatura do motorista e do vistoriador, relatório pronto para imprimir e controle automático dos vencimentos.

Além das vistorias, o app reúne aferição de fumaça (escala Ringelmann), diário de bordo, agenda, registro de ocorrências e gestão de usuários com permissões por cargo.

## Funcionalidades

### Vistoria eletromecânica
- **Vistoria Cavalo e Vistoria Carreta**: checklist por seções (freios, direção, vazamentos, elétrica…) com BOM / RUIM / N/A, progresso em tempo real e o botão "Restantes: Bom" para agilizar.
- **Revistoria**: carrega automaticamente o último laudo da placa para corrigir só o que foi reprovado.
- **Status automático**: qualquer item RUIM deixa o veículo **Não liberado** e gera a lista de serviços necessários.
- **Assinaturas** do motorista e do vistoriador na própria tela.
- **Relatórios** com visual próprio, prontos para imprimir ou salvar em PDF.
- **Pendências**: vistorias vencidas, a vencer e revistorias, com aviso pelo WhatsApp.

### Operacional
- **Aferição de fumaça** pela escala Ringelmann, com controle de vencimento por placa.
- **Diário de bordo**: checklist pré-carregamento com composição (Padrão, Rodotrem…) e tipo de carroceria.

### Gestão
- **Agenda** de vistorias em calendário, com marcação de vencidas, a vencer e em dia.
- **Ocorrências**: acidentes, incidentes e falhas mecânicas, com sugestão automática da causa a partir do relato.
- **Usuários e permissões**: cargos (Administrador, Inspetor, Escritório, Torre de Controle) e acesso por módulo.

### Experiência
- Login com Google (Credential Manager) ou e-mail e senha, com redefinição de senha.
- Tema claro e escuro.
- Navegação pelo menu lateral e painel inicial com os números do dia.
- Funciona offline: o Firestore sincroniza quando a conexão volta.

## Telas

> Os dados pessoais (nomes, e-mails, placas e empresas) aparecem borrados nos prints.

| Início | Tema escuro | Menu |
|:---:|:---:|:---:|
| <img src="docs/screenshots/01_inicio_claro.png" width="230"> | <img src="docs/screenshots/01b_inicio_escuro.png" width="230"> | <img src="docs/screenshots/02_menu_lateral.png" width="230"> |

| Nova vistoria | Checklist | Relatório |
|:---:|:---:|:---:|
| <img src="docs/screenshots/03_vistoria_nova.png" width="230"> | <img src="docs/screenshots/04_checklist.png" width="230"> | <img src="docs/screenshots/06_relatorio_vistoria.png" width="230"> |

| Realizadas | Pendências | Agenda |
|:---:|:---:|:---:|
| <img src="docs/screenshots/05_historico.png" width="230"> | <img src="docs/screenshots/07_pendencias.png" width="230"> | <img src="docs/screenshots/08_agenda.png" width="230"> |

| Aferição de fumaça | Pendências de fumaça | Diário de bordo |
|:---:|:---:|:---:|
| <img src="docs/screenshots/09_fumaca.png" width="230"> | <img src="docs/screenshots/10_fumaca_pendencias.png" width="230"> | <img src="docs/screenshots/11_diario_lista.png" width="230"> |

| Novo diário | Detalhe do diário | Ocorrências |
|:---:|:---:|:---:|
| <img src="docs/screenshots/12_diario_novo.png" width="230"> | <img src="docs/screenshots/13_diario_detalhe.png" width="230"> | <img src="docs/screenshots/14_ocorrencias.png" width="230"> |

| Usuários |
|:---:|
| <img src="docs/screenshots/15_usuarios.png" width="230"> |

## Tecnologias

- **Kotlin** e **Jetpack Compose** (Material 3), com design system próprio (`ui/components`, `ui/theme`)
- **Navigation Compose**
- **Firebase Authentication** (Google e e-mail/senha) e **Cloud Firestore**
- **Credential Manager** + Google ID para o login com Google
- **Coil** para a foto do perfil
- Android 7.0 (API 24) ou superior

## Como compilar

Pré-requisitos: Android Studio recente (AGP 9.2) e JDK 17 ou superior (o JBR do Android Studio serve).

1. Clone o repositório:
   ```bash
   git clone https://github.com/hsrodrigues/NexCheck.git
   ```
2. Crie um projeto no [Firebase Console](https://console.firebase.google.com/) com o pacote `com.nexcheck.app`, ative **Authentication** (Google e E-mail/Senha) e **Firestore**.
3. Baixe o `google-services.json` e coloque em `app/google-services.json`. Esse arquivo não é versionado.
4. Em `LoginScreen.kt`, troque o `webClientId` pelo ID do cliente Web (OAuth) do seu projeto Firebase.
5. Compile e instale:
   ```bash
   ./gradlew :app:installDebug
   ```

O número do build (`app/version.properties`) sobe sozinho a cada compilação e forma a versão `1.0.<build>`.

## Estrutura

```
app/src/main/java/com/nexcheck/app/
├── MainActivity.kt           # navegação e sessão
├── AppModules.kt             # módulos, permissões e perfil do usuário
├── AppDrawer.kt              # menu lateral
├── LoginScreen.kt
├── MainMenuScreen.kt         # painel inicial
├── InspectionScreen.kt       # vistoria (cavalo / carreta)
├── InspectionRepository.kt   # acesso ao Firestore
├── ReportTemplates.kt        # relatórios para impressão
├── ...                       # pendências, fumaça, diário, agenda, ocorrências, usuários
└── ui/                       # tema e componentes do design system
```

## Licença

Copyright © 2026 Hudson Santos Rodrigues. **Todos os direitos reservados.**

O código está público apenas para consulta. Não é permitido copiar, modificar, distribuir ou usar este software, no todo ou em parte, sem autorização prévia por escrito do autor. Veja [LICENSE](LICENSE).
