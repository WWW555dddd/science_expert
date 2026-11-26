package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;

class KnowledgeGraphOntologyServiceTest
{
    private final KnowledgeGraphOntologyService service = new KnowledgeGraphOntologyService();

    @Test
    void loadsVersionedOntologyWithCoreEntitiesAndRelations()
    {
        JSONObject ontology = service.getOntology();

        assertEquals(KnowledgeGraphOntologyService.ONTOLOGY_VERSION,
                ontology.getString("version"));
        assertNotNull(ontology.getJSONObject("entities").getJSONObject("Expert"));
        assertNotNull(ontology.getJSONObject("entities").getJSONObject("Capability"));
        assertNotNull(ontology.getJSONObject("entities").getJSONObject("Task"));
        assertNull(ontology.getJSONObject("entities").getJSONObject("CapabilityAssessment"));
        assertNull(ontology.getJSONObject("entities").getJSONObject("Certification"));
        assertNull(ontology.getJSONObject("entities").getJSONObject("Material"));
        assertNotNull(ontology.getJSONObject("relations").getJSONObject("EXPERTISE_IN"));
        assertNotNull(ontology.getJSONObject("relations").getJSONObject("HAS_EXPERT"));
        assertNull(ontology.getJSONObject("relations").getJSONObject("ASSESSED_AS"));
        assertNotNull(ontology.getJSONObject("relations").getJSONObject("REQUIRES_CAPABILITY"));
    }

    @Test
    void rejectsRelationDirectionOutsideOntology()
    {
        assertThrows(ServiceException.class, () ->
                service.requireRelationType("HAS_CREDENTIAL", "Organization", "Expert"));
    }
}
