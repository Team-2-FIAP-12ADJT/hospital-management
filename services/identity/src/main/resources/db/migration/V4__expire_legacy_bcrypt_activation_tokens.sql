-- Ticket 23: token_hash passou de bcrypt para SHA-256 (ActivationTokenHash)
-- sem migrar os tokens já emitidos. Uma linha antiga nunca mais casa com o
-- hash calculado na ativação, então fica presa como "válida" no expires_at
-- mas inalcançável. Expira essas linhas explicitamente em vez de deixá-las
-- silenciosamente órfãs.
UPDATE activation_tokens
SET expires_at = LEAST(expires_at, now())
WHERE consumed_at IS NULL
  AND token_hash !~ '^[0-9a-f]{64}$';
