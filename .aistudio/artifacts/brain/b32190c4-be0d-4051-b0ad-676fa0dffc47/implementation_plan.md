# NeoGBC — Emulador de Game Boy & Game Boy Color

O **NeoGBC** é um emulador nativo de Game Boy (DMG) e Game Boy Color (CGB) desenvolvido em Kotlin e Jetpack Compose com engine própria de alta precisão (CPU Sharp SM83/LR35902, PPU CGB, APU 4 canais estéreo e MBC1/MBC2/MBC3/MBC5), interface escura OLED com detalhes neon retrô, biblioteca inteligente com capas automáticas e controles táteis totalmente customizáveis.

## Funcionalidades Principais
- **Engine Nativa GB/GBC**: Emulação de ciclo preciso em Kotlin com suporte a cartuchos `.gb`, `.gbc`, `.cgb` e arquivos compactados `.zip`.
- **Biblioteca Inteligente**: Escaneamento de diretórios via Storage Access Framework (SAF), importação individual ou em lote, busca instantânea, ordenação alfabética (`A -> Z`), renomeação de jogos/arquivos no dispositivo, exclusão da biblioteca e capas automáticas (Libretro Thumbnails + gerador vetorial).
- **Controles Virtuais Customizáveis**: Editor interativo com ajuste de posição, escala e opacidade individual por botão (D-Pad, B+A, Select+Start e Barra Turbo), bloqueio anticolisão entre controles e proteção para não invadir a tela do jogo nem sair da tela do aparelho.
- **Save States, SRAM & Cheats**: Slots de Save State com miniatura PNG real, persistência automática de bateria SRAM (`.sav`) e suporte a códigos GameShark e Game Genie.
