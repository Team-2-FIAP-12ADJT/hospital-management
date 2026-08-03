# Identity

Contexto dono das pessoas do sistema e do direito de acesso delas. É a única
fonte de credencial, papel e contato; os demais contextos referenciam pessoas
apenas por identificador.

## Language

**User**:
Conta de acesso ao sistema — credencial e papel. Uma pessoa tem exatamente um
User, e é o User que autentica, nunca o Patient ou o Doctor.
_Avoid_: Account, Login, Principal

**Role** (spec: _nível de acesso_):
O papel que determina o que um User pode fazer: Patient, Doctor ou Nurse.
_Avoid_: Permission, Profile, Authority, Perfil

**Patient** (spec: _paciente_):
Pessoa que recebe atendimento. Dona do contato para onde as Notifications vão.
_Avoid_: Client, Customer

**Doctor** (spec: _médico_):
Profissional para quem Appointments são marcadas e que produz o dado clínico.
_Avoid_: Physician, Practitioner, Medic

**Nurse** (spec: _enfermeiro_):
Profissional que registra e altera Appointments em nome de um Doctor.
_Avoid_: Attendant

**Contact**:
O endereço por onde um Patient recebe Notifications.
_Avoid_: Address, Email, Contato
