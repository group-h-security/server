PASSWORD="$(openssl rand -base64 24)"
# ensure directory exists

rm -f stores/keystorePass.txt stores/server-keystore.p12 stores/server-keystore.jks certs/dummy-cert.pem stores/server-key.pem
mkdir -p stores certs

# clear any existing password file (truncate to zero bytes)
echo "$PASSWORD" > stores/keystorePass.txt

# Generate Private key for the server
openssl genrsa -out stores/server-key.pem 4096

# temp cert so we can store make the jks with the private key entry
openssl req -x509 -new -key stores/server-key.pem -days 1 \
  -subj "/C=IE/O=Group-H Security/CN=temporary" \
  -out certs/dummy-cert.pem

openssl pkcs12 -export -in certs/dummy-cert.pem -inkey stores/server-key.pem \
  -out stores/server-keystore.p12 -name server -passout pass:"$PASSWORD"

keytool -importkeystore \
  -srckeystore stores/server-keystore.p12 -srcstoretype PKCS12 \
  -srcstorepass "$PASSWORD" \
  -destkeystore stores/server-keystore.jks -storepass "$PASSWORD" \
  -deststorepass "$PASSWORD" -noprompt
echo "✓ Server keystore created (server-keystore.jks)"


