#!/usr/bin/env sh
# written by gpt-5

set -eu

CERT_DIR="certs"
HOSTPORT="localhost:8443"

need() {
  for f in "$@"; do
    [ -f "$f" ] || { echo "missing file: $f" >&2; exit 1; }
  done
}

echo "Select test: [v]alid (present client cert) or [i]nvalid (no client cert)"
printf "> "
read ans

case "$ans" in
  v|V)
    need "$CERT_DIR/client-cert.pem" "$CERT_DIR/client-key.pem" "$CERT_DIR/ca-cert.pem"
    echo "Running valid mTLS client (TLS 1.2, with client cert/key, trusting CA)..."
    exec openssl s_client \
      -connect "$HOSTPORT" \
      -no_tls1_3 \
      -cert "$CERT_DIR/client-cert.pem" \
      -key  "$CERT_DIR/client-key.pem" \
      -CAfile "$CERT_DIR/ca-cert.pem" \
      -servername localhost \
      -verify_return_error
    ;;
  i|I)
    need "$CERT_DIR/ca-cert.pem"
    echo "Running invalid client (TLS 1.2, no client cert; should fail handshake)..."
    exec openssl s_client \
      -connect "$HOSTPORT" \
      -no_tls1_3 \
      -CAfile "$CERT_DIR/ca-cert.pem" \
      -servername localhost \
      -verify_return_error
    ;;
  *)
    echo "invalid choice" >&2
    exit 2
    ;;
esac

