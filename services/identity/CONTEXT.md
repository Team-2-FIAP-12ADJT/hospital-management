# Identity

Contexto dono do direito de acesso: credencial, papel e estado de conta. **Não é
dono de nenhuma pessoa** — Patient e Doctor pertencem ao
[Scheduling](../scheduling/CONTEXT.md), e aqui aparecem apenas como o
identificador que chega nos eventos de cadastro.

## Language

**User**:
Conta de acesso ao sistema — credencial, papel e estado. Compartilha o
identificador do participante correspondente: o `id` de um User é o mesmo `id` do
Patient ou do Doctor que o originou.
_Avoid_: Account, Login, Principal, Person

**Role** (spec: _nível de acesso_):
O papel que determina o que um User pode fazer: Patient, Doctor ou Nurse. Chega
no evento de cadastro e nunca é informado pelo cliente.
_Avoid_: Permission, Profile, Authority, Perfil

**Activation Token**:
Segredo de uso único e prazo limitado, entregue por e-mail, que autoriza a pessoa
a definir a própria senha. O Identity guarda apenas o hash.
_Avoid_: Invite, Reset Token, Magic Link

**Pending Activation**:
Estado de um User provisionado a partir do evento de cadastro e ainda sem senha.
Não autentica.
_Avoid_: Inactive, Disabled, Draft

**Seeded Account**:
User criado pela migração inicial, já ativo e com credencial conhecida. Existe
para quebrar a circularidade do primeiro cadastro profissional e para que o login
não dependa da cadeia assíncrona.
_Avoid_: Demo User, Fixture, Admin
