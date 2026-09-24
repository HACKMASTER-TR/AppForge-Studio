"""Offline SQLite reference tests. Does not claim to test remote D1."""
import sqlite3
from pathlib import Path
import uuid

root = Path(__file__).resolve().parents[1]
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
for path in sorted((root / 'migrations').glob('*.sql')):
    db.executescript(path.read_text())

id1, id2, install = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
admin, thumb, pub = 'a'*64, 't'*64, 'example-public-key'
now = 1000000
for code_id, hash_value in [(id1, 'hash1'), (id2, 'hash2')]:
    db.execute('''INSERT INTO pro_activation_codes
      (id, code_hash, state, created_by_hash, created_at, expires_at, updated_at)
      VALUES (?, ?, 'issued', ?, ?, ?, ?)''',
      (code_id, hash_value, admin, now, now+9999, now))
db.execute('''INSERT INTO installations(id,key_thumbprint,state,created_at)
 VALUES (?,?,'active',?)''', (install,thumb,now))
db.execute('INSERT INTO pro_installation_keys VALUES (?,?)', (install,pub))
db.execute('''UPDATE pro_activation_codes SET state='redeemed',
 redeemed_at=?, redeemed_by_thumbprint=?, redemption_id=?
 WHERE id=? AND state='issued' ''', (now,thumb,'redeem1',id1))
db.execute('''INSERT INTO pro_admin_grants
 (installation_id,activation_code_id,state,granted_at,updated_at)
 VALUES (?,?,'active',?,?)''', (install,id1,now,now))
db.commit()
assert db.execute("SELECT state FROM pro_admin_grants WHERE installation_id=?",(install,)).fetchone()[0]=='active'

# Enforce revocation without modifying the used activation code.
result=db.execute('''UPDATE pro_admin_grants SET state='revoked',
 revoked_at=?, revoked_by_hash=?, updated_at=? WHERE installation_id=? AND state='active' ''',
 (now+1,admin,now+1,install))
assert result.rowcount==1
assert db.execute("SELECT state FROM pro_activation_codes WHERE id=?",(id1,)).fetchone()[0]=='redeemed'
assert db.execute("SELECT state FROM pro_admin_grants WHERE installation_id=?",(install,)).fetchone()[0]=='revoked'

# Simulate the D1 batch transaction, including its final SQL abort guard.
challenge_id=str(uuid.uuid4())
nonce_hash='n'*64
db.execute('''INSERT INTO installation_challenges
 (id,installation_id,nonce_hash,request_nonce_hash,created_at,expires_at)
 VALUES (?,?,?,?,?,?)''',
 (challenge_id,install,nonce_hash,'r'*64,now,now+120))
db.commit()

code_sql='''UPDATE pro_activation_codes SET state='redeemed', redeemed_at=?,
 redeemed_by_thumbprint=?, redemption_id=?, updated_at=?
 WHERE id=? AND state='issued' AND expires_at>?
 AND EXISTS(SELECT 1 FROM installation_challenges WHERE id=?
 AND installation_id=? AND nonce_hash=? AND consumption_id=?)'''

def reactivation(code_id, redemption_id, wrong_challenge=False):
    db.execute('BEGIN')
    try:
        consumed=db.execute('''UPDATE installation_challenges SET consumed_at=?, consumption_id=?
         WHERE id=? AND installation_id=? AND nonce_hash=? AND consumed_at IS NULL
         AND expires_at>? AND EXISTS(SELECT 1 FROM pro_admin_grants
         WHERE installation_id=? AND state='revoked')''',
         (now+2,redemption_id,challenge_id,install,nonce_hash,now+2,install))
        changed=db.execute(code_sql,(now+2,thumb,redemption_id,now+2,code_id,
           now+2, challenge_id if not wrong_challenge else str(uuid.uuid4()),
           install,nonce_hash,redemption_id))
        grant=db.execute('''UPDATE pro_admin_grants
          SET activation_code_id=?,state='active',granted_at=?,updated_at=?,
          revoked_at=NULL,revoked_by_hash=NULL
          WHERE installation_id=? AND state='revoked'
          AND EXISTS(SELECT 1 FROM pro_activation_codes WHERE id=?
          AND state='redeemed' AND redemption_id=? AND redeemed_by_thumbprint=?)''',
          (code_id,now+2,now+2,install,code_id,redemption_id,thumb))
        # The last receipt cannot contain NULL; it aborts and rolls back
        # when any earlier conditional statement did not establish a grant.
        db.execute('''INSERT INTO pro_redemption_receipts
        (redemption_id,activation_code_id,installation_id,created_at)
        VALUES((SELECT CASE WHEN EXISTS(SELECT 1 FROM pro_activation_codes
          WHERE id=? AND state='redeemed' AND redemption_id=?)
          AND EXISTS(SELECT 1 FROM pro_admin_grants WHERE installation_id=?
          AND activation_code_id=? AND state='active')
          THEN ? ELSE NULL END),?,?,?)''',
          (code_id,redemption_id,install,code_id,redemption_id,code_id,install,now+2))
        assert (consumed.rowcount, changed.rowcount, grant.rowcount)==(1,1,1)
        db.commit()
        return True
    except (sqlite3.IntegrityError, AssertionError):
        db.rollback()
        return False

assert reactivation(id2,'redeem2',wrong_challenge=True) is False
assert db.execute("SELECT state FROM pro_activation_codes WHERE id=?",(id2,)).fetchone()[0]=='issued'
assert db.execute("SELECT consumed_at FROM installation_challenges WHERE id=?",(challenge_id,)).fetchone()[0] is None
assert reactivation(id2,'redeem2') is True
assert db.execute("SELECT activation_code_id FROM pro_admin_grants WHERE installation_id=?",(install,)).fetchone()[0]==id2
archived=db.execute('''SELECT activation_code_id,revoked_at,revoked_by_hash
 FROM pro_admin_grant_history WHERE installation_id=?''',(install,)).fetchone()
assert archived==(id1,now+1,admin)
assert reactivation(id2,'redeem3') is False
assert db.execute("SELECT COUNT(*) FROM pro_redemption_receipts WHERE installation_id=?",(install,)).fetchone()[0]==1
assert db.execute("SELECT state FROM pro_activation_codes WHERE id=?",(id1,)).fetchone()[0]=='redeemed'
print('SQLITE_LIFECYCLE=PASS')
print('REVOKED_HISTORY=PASS')
print('ROLLBACK_GUARD=PASS')
print('CHALLENGE_REPLAY=BLOCKED')
print('USED_CODE_REPLAY=BLOCKED')
