# Monorepo Maven multi-módulo

A entrega é avaliada a partir de um único repositório aberto que os professores
vão clonar, então os cinco serviços vivem em um monorepo com pom pai na raiz
gerenciando versões e um `docker-compose.yml` único. Aceitamos conscientemente a
impureza de microsserviços que compartilham parent pom: a alternativa polyrepo
custaria cinco links e documentação espalhada, sem ganho dentro do escopo da
avaliação.
