#!/usr/bin/env python3
"""Genera un JWT HS256 de prueba firmado con JWT_SECRET (de la variable de entorno o del archivo .env).

Uso:
    python3 scripts/generar_token.py [usuario] [permisos] [minutos]
    python3 scripts/generar_token.py ana.perez "ordenes:crear ordenes:leer" 30
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time
from pathlib import Path

PERMISOS_POR_DEFECTO = "ordenes:crear ordenes:leer ordenes:actualizar-estado"


def leer_secreto():
    if os.environ.get("JWT_SECRET"):
        return os.environ["JWT_SECRET"]
    env = Path(__file__).resolve().parent.parent / ".env"
    if env.exists():
        for linea in env.read_text(encoding="utf-8").splitlines():
            if linea.startswith("JWT_SECRET="):
                return linea.split("=", 1)[1].strip()
    sys.exit("No se encontró JWT_SECRET en el entorno ni en .env")


def base64url(datos):
    return base64.urlsafe_b64encode(datos).rstrip(b"=").decode()


def main():
    usuario = sys.argv[1] if len(sys.argv) > 1 else "usuario.prueba"
    permisos = sys.argv[2] if len(sys.argv) > 2 else PERMISOS_POR_DEFECTO
    minutos = int(sys.argv[3]) if len(sys.argv) > 3 else 60

    ahora = int(time.time())
    cabecera = base64url(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    datos = base64url(json.dumps({"sub": usuario, "scope": permisos, "iat": ahora, "exp": ahora + minutos * 60}).encode())
    firma = hmac.new(leer_secreto().encode(), f"{cabecera}.{datos}".encode(), hashlib.sha256).digest()
    print(f"{cabecera}.{datos}.{base64url(firma)}")


if __name__ == "__main__":
    main()
