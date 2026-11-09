package demo.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_record", schema = "audit")
public class AuditRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String note;

    protected AuditRecord() {
    }

    public AuditRecord(String note) {
        this.note = note;
    }

    public Long getId() { return id; }
    public String getNote() { return note; }
}
