# BLE-WiFi Bridge

App Android (Kotlin) que conecta via **BLE** a um ESP32 e repassa os dados
recebidos via **WebSocket (WiFi)** para um servidor no computador.

## Build automático pelo GitHub Actions

Este projeto já vem com `.github/workflows/build-apk.yml`. Ao subir para
um repositório no GitHub, ele compila o `.apk` automaticamente na aba
**Actions**, sem precisar instalar nada localmente. Veja o artifact
`app-debug-apk` ao final da execução.

## Como testar rapidamente (sem o ESP32 ainda)

1. No computador, instale a dependência do servidor de teste:
   ```
   pip install websockets
   ```
2. Rode o servidor:
   ```
   python servidor_teste.py
   ```
3. Descubra o IP do computador na rede WiFi (ex: `192.168.0.10`).
4. No app, digite esse IP e a porta `8765`, toque em **Conectar**.

## UUIDs usados (Nordic UART Service)

```
Serviço:  6E400001-B5A3-F393-E0A9-E50E24DCCA9E
TX (ESP32 -> celular, notify): 6E400003-B5A3-F393-E0A9-E50E24DCCA9E
RX (celular -> ESP32, write):  6E400002-B5A3-F393-E0A9-E50E24DCCA9E
```

Use exatamente esses UUIDs no firmware do ESP32 para que o app reconheça
o dispositivo automaticamente.

## Fluxo de dados

```
ESP32 --(BLE notify)--> App Android --(WebSocket)--> servidor_teste.py (ou servidor real)
```
