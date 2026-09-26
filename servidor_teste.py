"""
Servidor WebSocket simples para testar o app BLE-WiFi Bridge.

Instalar dependência:
    pip install websockets

Rodar:
    python servidor_teste.py

O servidor escuta em 0.0.0.0:8765 e imprime tudo que o celular enviar.
Descubra o IP do seu computador na mesma rede WiFi do celular
(ex: `ipconfig` no Windows ou `ip addr` no Linux/Mac) e digite esse IP
no app do celular.
"""

import asyncio
import websockets

async def handler(websocket):
    print(f"[+] Celular conectado: {websocket.remote_address}")
    try:
        async for message in websocket:
            print(f"[ESP32 via celular] {message}")
            # opcional: responder de volta ao celular
            # await websocket.send("recebido")
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        print(f"[-] Celular desconectado: {websocket.remote_address}")

async def main():
    async with websockets.serve(handler, "0.0.0.0", 8765):
        print("Servidor WebSocket rodando em ws://0.0.0.0:8765")
        await asyncio.Future()  # roda para sempre

if __name__ == "__main__":
    asyncio.run(main())
