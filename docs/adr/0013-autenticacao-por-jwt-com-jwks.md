# Autenticação por JWT com JWKS

O Identity valida a credencial e emite um JWT assinado com par de chaves,
publicando a chave pública em um endpoint JWKS; gateway e serviços se configuram
como resource server apontando para esse `jwk-set-uri` e validam a assinatura
offline, sem chamada ao Identity no caminho da request.

O enunciado pede "autenticação básica com Spring Security", e HTTP Basic literal
foi considerado. Foi recusado porque obrigaria cada serviço a verificar
credencial a cada request — ou chamando o Identity, o que acopla cinco serviços
no caminho quente, ou compartilhando a tabela de usuário, o que quebra o dono
único fixado no ADR-0006. Segredo HMAC compartilhado foi recusado pelo motivo
oposto: qualquer um dos cinco serviços poderia forjar um token.

## Consequences

O Identity precisa expor o endpoint JWKS, e a rotação de chave passa a ser
transparente para os demais serviços. Como a leitura literal de "autenticação
básica" pode não bater com JWT, a escolha e o motivo precisam estar escritos no
README, sob pena de quem avaliar ler como requisito não atendido.
