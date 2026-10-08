package com.dsms.candidates;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
class ExpertSkillId implements Serializable {
    private Long expertId;
    private Long skillId;
}

@Getter
@Setter
@Entity
@Table(name = "expert_skill")
class ExpertSkill {
    @EmbeddedId
    private ExpertSkillId id;
    @Column(nullable = false)
    private String proficiency;
}
