-- Anonymous confirmation capability: no account, email, password or raw code.
CREATE TABLE account_deletion_receipts (
 code_hash CHAR(64) PRIMARY KEY,
 status VARCHAR(10) NOT NULL CHECK (status='DELETED'),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_deletion_receipt_expiry ON account_deletion_receipts(expires_at);
