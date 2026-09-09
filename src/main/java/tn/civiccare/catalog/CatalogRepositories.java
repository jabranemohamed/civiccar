package tn.civiccare.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CategoryGroupRepository extends JpaRepository<CategoryGroup, UUID> {
    List<CategoryGroup> findAllByOrderBySort();
}

interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {
    Optional<ServiceType> findByCode(String code);

    @Query("select st from ServiceType st join fetch st.group g where st.active and g.active order by g.sort, st.sort")
    List<ServiceType> findAllActive();

    @Query("select st from ServiceType st join fetch st.group g order by g.sort, st.sort")
    List<ServiceType> findAllWithGroup();
}

interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {
    Optional<FieldDefinition> findByCode(String code);
}

interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {
    Optional<RoutingRule> findByServiceTypeIdAndActiveTrue(UUID serviceTypeId);

    List<RoutingRule> findAll();
}
