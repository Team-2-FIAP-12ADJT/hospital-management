FROM alpine:3.20

RUN apk add --no-cache bash curl jq

WORKDIR /app
COPY register.sh /app/register.sh
RUN chmod +x /app/register.sh

ENTRYPOINT ["/app/register.sh"]
