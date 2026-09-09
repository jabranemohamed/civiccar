package tn.civiccare.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.identity.Department;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryGroupRepository groups;
    private final ServiceTypeRepository types;
    private final FieldDefinitionRepository fields;
    private final RoutingRuleRepository routingRules;

    public CatalogService(CategoryGroupRepository groups, ServiceTypeRepository types,
                          FieldDefinitionRepository fields, RoutingRuleRepository routingRules) {
        this.groups = groups;
        this.types = types;
        this.fields = fields;
        this.routingRules = routingRules;
    }

    public List<CategoryGroup> allGroups() {
        return groups.findAllByOrderBySort();
    }

    /** Types actifs avec leur famille, pour le sélecteur public. */
    public List<ServiceType> activeTypes() {
        List<ServiceType> list = types.findAllActive();
        // initialisation des champs conditionnels dans la transaction
        list.forEach(t -> t.getFields().forEach(f -> f.getOptions().size()));
        return list;
    }

    /** Tous les types (y compris inactifs) pour le back-office. */
    public List<ServiceType> allTypes() {
        List<ServiceType> list = types.findAllWithGroup();
        list.forEach(t -> t.getFields().forEach(f -> f.getOptions().size()));
        return list;
    }

    public Optional<ServiceType> typeByCode(String code) {
        return types.findByCode(code).map(t -> {
            t.getGroup().getCode();
            t.getFields().forEach(f -> f.getOptions().size());
            return t;
        });
    }

    public Optional<ServiceType> typeById(UUID id) {
        return types.findById(id).map(t -> {
            t.getGroup().getCode();
            t.getFields().forEach(f -> f.getOptions().size());
            return t;
        });
    }

    /**
     * Recherche Unicode insensible aux diacritiques (FR) et robuste pour l'arabe.
     * La normalisation s'applique à la requête et aux libellés comparés, jamais au texte stocké.
     */
    public List<ServiceType> searchTypes(String query, Locale locale) {
        String q = normalize(query);
        return activeTypes().stream()
                .filter(t -> normalize(t.label(locale)).contains(q)
                        || normalize(t.getGroup().label(locale)).contains(q)
                        || normalize(t.getLabelFr()).contains(q)
                        || normalize(t.getLabelAr()).contains(q))
                .toList();
    }

    /** Normalisation documentée : NFKD + suppression des diacritiques latins et arabes (tashkil). */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        // Variantes arabes courantes : alef, ta marbuta, ya
        return n.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي');
    }

    @Transactional
    public void setTypeActive(UUID typeId, boolean active) {
        types.findById(typeId).ifPresent(t -> t.setActive(active));
    }

    public Optional<Department> routeFor(UUID serviceTypeId) {
        return routingRules.findByServiceTypeIdAndActiveTrue(serviceTypeId)
                .map(r -> {
                    Department d = r.getDepartment();
                    d.getCode();
                    return d;
                });
    }

    public List<RoutingRule> allRoutingRules() {
        List<RoutingRule> rules = routingRules.findAll();
        rules.forEach(r -> {
            r.getServiceType().getCode();
            r.getDepartment().getCode();
        });
        return rules;
    }

    @Transactional
    public void updateRouting(UUID ruleId, Department department, boolean active) {
        routingRules.findById(ruleId).ifPresent(r -> {
            r.setDepartment(department);
            r.setActive(active);
        });
    }

    public Optional<FieldDefinition> fieldByCode(String code) {
        return fields.findByCode(code).map(f -> {
            f.getOptions().size();
            return f;
        });
    }
}
