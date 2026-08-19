# ROTA+ — Otimizador de rotas de entrega

Aplicativo Android independente que decide **quais entregas fazer primeiro e em qual ordem**,
a partir da sua posição atual. Não substitui o Envio Logistics — roda ao lado dele.

**Etapa 1 (esta versão) entrega:** localização atual, cadastro em massa de endereços,
geocodificação, agrupamento geográfico, seleção do melhor lote, ordenação, distância,
tempo estimado, combustível, previsão de término, meta, comparativo, histórico e
navegação no Google Maps.

---

## Custo de operação: R$ 0,00

Nenhuma chave de API é necessária.

| Serviço | Chave | Custo | Onde é usado |
|---|---|---|---|
| `android.location.Geocoder` (nativo) | não | grátis | 1ª tentativa de geocodificação |
| Nominatim / OpenStreetMap | não | grátis (1 req/s) | fallback de geocodificação + tipo do local |
| OSRM demo (`router.project-osrm.org`) | não | grátis, sem SLA | matriz de distância real (opcional, desligado por padrão) |
| Google Maps | não | grátis | só como *Intent* de navegação |

Se um dia quiser Google Routes API: crie a chave em `console.cloud.google.com` →
APIs & Services → Routes API, ative billing (US$ 200/mês de crédito grátis, ~5 mil
matrizes), e substitua `geo/Osrm.kt`. **Não é preciso para usar o app.**

---

## Como compilar

### Opção A — GitHub Actions (recomendado num notebook de 4 GB)

1. Crie um repositório no GitHub e suba esta pasta.
2. Aba **Actions** → workflow **Build APK** → **Run workflow**.
3. Ao terminar, baixe `rotamais-debug-apk` em **Artifacts**.

Zero build local, zero Android Studio.

### Opção B — Android Studio

1. Instale o Android Studio (Ladybug ou mais novo).
2. **File → Open** → selecione `C:\Users\ofici\rotamais`.
3. Aceite baixar o SDK 35 e o Gradle 8.9 quando for perguntado.
4. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
5. O APK sai em `app\build\outputs\apk\debug\app-debug.apk`.

### Opção C — linha de comando

```bash
cd C:/Users/ofici/rotamais && ./gradlew assembleDebug
```

Se `gradlew` não existir ainda, gere o wrapper uma vez com o Gradle instalado:

```bash
cd C:/Users/ofici/rotamais && gradle wrapper --gradle-version 8.9
```

### Instalar no celular

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou copie o `.apk` para o celular, abra pelo gerenciador de arquivos e autorize
"instalar de fontes desconhecidas".

---

## Primeiro teste (o cenário do item 30)

1. Abra o app em Sombrio, autorize a localização, toque em **ATUALIZAR LOCALIZAÇÃO**.
2. Aba **Entregas** → cole os 20 endereços, um por linha → **ADICIONAR**.
3. **GEOCODIFICAR** (leva ~1 s por endereço quando cai no Nominatim).
4. Volte ao **Início** → **OTIMIZAR ROTA**.
5. A aba **Rota** mostra as 10 melhores, na ordem, com km, tempo, custo e previsão.
6. **NAVEGAR** abre o Google Maps. **ENTREGUE** marca a parada, move sua origem para
   ali e recalcula o próximo lote a partir dali.

Formatos aceitos na colagem:

```
Rua Getúlio Vargas, 123, Centro, Araranguá
22 | Rua Getúlio Vargas, 123, Centro, Araranguá
22 | Mercado São José | -28.93510,-49.49170
```

---

## Como o algoritmo escolhe as 10

`otim/Otimizador.kt`

1. **DBSCAN** (raio 2,5 km) descobre as regiões — Araranguá, Sombrio, Turvo…
2. Cada região recebe uma pontuação `nº de entregas ÷ (distância até o centroide + 1)`.
   8 entregas a 3 km = 2,00. 1 entrega a 15 km = 0,06. É o que impede o desvio burro.
3. O lote é preenchido pela região vencedora. Se sobrar vaga, a próxima região é
   medida **a partir do centroide da região já escolhida**, não da sua posição inicial —
   é isso que evita `A → B → A → C → A`.
4. **Nearest Neighbor + 2-opt** define a sequência (caminho aberto, sem voltar ao início).
5. **Comércio** só desempata: entre candidatos dentro de 125% + 150 m da melhor
   distância, e num passe final que só troca a ordem se o custo subir ≤ 0,4 km.

---

## Ajustes (aba Consumo)

| Campo | Padrão |
|---|---|
| Veículo | Ford Fiesta Sedan 2013 |
| Consumo | 9,0 km/L |
| Gasolina | R$ 5,80/L |
| Tempo por parada | 3 min (o app sugere o seu valor real após 5 entregas) |
| Velocidade média | 35 km/h |
| Fator rodoviário | 1,35 |
| Entregas por lote | 10 |
| Raio de agrupamento | 2,5 km |
| Meta da rota | 5 h |

---

## Limitações conhecidas

- Sem OSRM, a distância é **estimativa** (linha reta × 1,35). Todo número marcado
  como "estimativa" na tela é exatamente isso — só o odômetro confirma o real.
- Nominatim limita 1 requisição por segundo: 40 endereços ≈ 45 s, uma vez só
  (o resultado fica salvo no banco).
- Endereço rural sem número pode não geocodificar. O app marca em vermelho e você
  cola `lat,lon` na mão.
- Sem trânsito em tempo real na V1.

## Próximas etapas

- **Etapa 4** — `AccessibilityService` só de leitura sobre o Envio Logistics, com
  fallback de OCR (ML Kit). Nada de engenharia reversa, nada de confirmar entrega
  automaticamente.
- Distribuição por APK direto (uso pessoal), não pela Play Store.
