package io.crnk.core.engine.internal.document.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.crnk.core.boot.CrnkProperties;
import io.crnk.core.engine.document.ResourceIdentifier;
import io.crnk.core.engine.information.resource.ResourceField;
import io.crnk.core.engine.information.resource.ResourceInformation;
import io.crnk.core.engine.internal.dispatcher.path.PathBuilder;
import io.crnk.core.engine.internal.utils.SerializerUtil;
import io.crnk.core.engine.properties.PropertiesProvider;
import io.crnk.core.engine.query.QueryAdapter;
import io.crnk.core.engine.query.QueryContext;
import io.crnk.core.engine.registry.RegistryEntry;
import io.crnk.core.engine.registry.ResourceRegistry;
import io.crnk.core.queryspec.PathSpec;
import io.crnk.core.resource.links.LinksInformation;
import io.crnk.core.resource.links.RelatedLinksInformation;
import io.crnk.core.resource.links.SelfLinksInformation;
import io.crnk.core.resource.list.LinksContainer;
import io.crnk.core.resource.meta.MetaContainer;
import io.crnk.core.resource.meta.MetaInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DocumentMapperUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMapper.class);

    private ResourceRegistry resourceRegistry;

    private ObjectMapper objectMapper;

    private static SerializerUtil serializerUtil;

    public DocumentMapperUtil(ResourceRegistry resourceRegistry, ObjectMapper objectMapper,
                              PropertiesProvider propertiesProvider) {
        this.resourceRegistry = resourceRegistry;
        this.objectMapper = objectMapper;

        boolean serializeLinksAsObjects =
                Boolean.parseBoolean(propertiesProvider.getProperty(CrnkProperties.SERIALIZE_LINKS_AS_OBJECTS));
        serializerUtil = new SerializerUtil(serializeLinksAsObjects);
    }

    protected static List<ResourceField> getRequestedFields(ResourceInformation resourceInformation, QueryAdapter queryAdapter,
                                                            List<ResourceField> fields, boolean relation) {
        Map<String, Set<PathSpec>> includedFieldsSet = queryAdapter != null ? queryAdapter.getIncludedFields() : null;
        Set<PathSpec> includedFields = includedFieldsSet != null ? includedFieldsSet.get(resourceInformation.getResourceType()) : null;
        if (noResourceIncludedFieldsSpecified(includedFields)) {
            return fields;
        } else {
            return computeRequestedFields(includedFields, relation, queryAdapter, resourceInformation, fields);
        }
    }

    private static List<ResourceField> computeRequestedFields(Set<PathSpec> includedFields, boolean relation,
                                                              QueryAdapter queryAdapter, ResourceInformation resourceInformation, List<ResourceField> fields) {

        if (relation) {
            // for relations consider both "include" and "fields"
            Map<String, Set<PathSpec>> includedRelationsSet = queryAdapter.getIncludedRelations();
            Set<PathSpec> includedRelations = includedRelationsSet != null ? includedRelationsSet.get(resourceInformation.getResourceType()) : null;
            if (includedRelations != null) {
                includedFields = new HashSet<>(includedFields);
                for (PathSpec include : includedRelations) {
                    includedFields.add(include);
                }
            }
        }

        List<ResourceField> results = new ArrayList<>();
        for (ResourceField field : fields) {
            if (isIncluded(field, includedFields)) {
                results.add(field);
            }
        }
        return results;
    }

    private static boolean isIncluded(ResourceField field, Set<PathSpec> includedFields) {
        // TODO remove use of field.getJsonName, here to maintain backward compatibility

        return includedFields.contains(PathSpec.of(field.getUnderlyingName()))
                || includedFields.contains(PathSpec.of(field.getJsonName()));
    }

    protected static boolean noResourceIncludedFieldsSpecified(Set<PathSpec> typeIncludedFields) {
        return typeIncludedFields == null || typeIncludedFields.isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> List<T> toList(Object entity) {
        if (entity instanceof List) {
            return (List) entity;
        } else if (entity instanceof Iterable) {
            ArrayList<T> result = new ArrayList<>();
            for (Object element : (Iterable) entity) {
                result.add((T) element);
            }
            return result;
        } else {
            return Collections.singletonList((T) entity);
        }
    }

    public String getRelationshipLink(ResourceInformation resourceInformation, Object entity, ResourceField field,
                                      boolean related, QueryContext queryContext) {
        String resourceUrl = resourceRegistry.getResourceUrl(queryContext, entity);
        return resourceUrl + (!related ? "/" + PathBuilder.RELATIONSHIP_MARK + "/" : "/") + field
                .getJsonName();
    }

    public List<ResourceIdentifier> toResourceIds(Collection<?> entities) {
        List<ResourceIdentifier> results = new ArrayList<>();
        for (Object entity : entities) {
            results.add(toResourceId(entity));
        }
        return results;
    }

    public ResourceIdentifier toResourceId(Object entity) {
        if (entity == null) {
            return null;
        }
        RegistryEntry entry = resourceRegistry.findEntry(entity.getClass());
        ResourceInformation resourceInformation = entry.getResourceInformation();
        return resourceInformation.toResourceIdentifier(entity);
    }

    public void setLinks(LinksContainer container, LinksInformation linksInformation, QueryAdapter queryAdapter) {
        if (linksInformation != null) {
            LOGGER.debug("adding links information {}", linksInformation);
            container.setLinks(objectMapper.valueToTree(linksInformation));
        }
        if (queryAdapter != null && queryAdapter.getCompactMode()) {
            ObjectNode links = container.getLinks();
            if (links != null) {
                links.remove("self");
                if (!links.fieldNames().hasNext()) {
                    container.setLinks(null);
                }
            }

        }
    }

    public void setMeta(MetaContainer container, MetaInformation metaInformation) {
        if (metaInformation != null) {
            LOGGER.debug("adding meta information {}", metaInformation);
            container.setMeta(objectMapper.valueToTree(metaInformation));
        }
    }

    public ResourceInformation getResourceInformation(Class<?> dataClass) {
        return resourceRegistry.findEntry(dataClass).getResourceInformation();
    }

    public ResourceInformation getResourceInformation(String resourceType) {
        return resourceRegistry.getEntry(resourceType).getResourceInformation();
    }

    public String getSelfUrl(QueryContext queryContext, ResourceInformation resourceInformation, Object entity) {
        return resourceRegistry.getResourceUrl(queryContext, entity);
    }

    public static SerializerUtil getSerializerUtil() {
        return serializerUtil;
    }

    @JsonInclude(Include.NON_EMPTY)
    protected static class DefaultSelfRelatedLinksInformation implements SelfLinksInformation, RelatedLinksInformation {

        @JsonInclude(Include.NON_EMPTY)
        private String related;

        @JsonInclude(Include.NON_EMPTY)
        private String self;

        @Override
        public String getRelated() {
            return related;
        }

        @Override
        public void setRelated(String related) {
            this.related = related;
        }

        @Override
        public String getSelf() {
            return self;
        }

        @Override
        public void setSelf(String self) {
            this.self = self;
        }

    }
}
