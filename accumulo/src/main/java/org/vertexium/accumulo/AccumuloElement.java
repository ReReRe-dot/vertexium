package org.vertexium.accumulo;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.io.Text;
import org.vertexium.*;
import org.vertexium.accumulo.iterator.ElementIterator;
import org.vertexium.historicalEvent.HistoricalEvent;
import org.vertexium.historicalEvent.HistoricalEventId;
import org.vertexium.mutation.*;
import org.vertexium.property.MutableProperty;
import org.vertexium.query.ExtendedDataQueryableIterable;
import org.vertexium.query.QueryableIterable;
import org.vertexium.search.IndexHint;
import org.vertexium.util.IncreasingTime;
import org.vertexium.util.PropertyCollection;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

import static org.vertexium.util.StreamUtils.toIterable;

public abstract class AccumuloElement extends ElementBase implements Serializable, HasTimestamp {
    private static final long serialVersionUID = 1L;
    public static final Text CF_PROPERTY = ElementIterator.CF_PROPERTY;
    public static final Text CF_PROPERTY_METADATA = ElementIterator.CF_PROPERTY_METADATA;
    public static final Text CF_PROPERTY_SOFT_DELETE = ElementIterator.CF_PROPERTY_SOFT_DELETE;
    public static final Text CF_EXTENDED_DATA = ElementIterator.CF_EXTENDED_DATA;
    public static final Value SOFT_DELETE_VALUE = ElementIterator.SOFT_DELETE_VALUE;
    public static final Value SOFT_DELETE_VALUE_DELETED = ElementIterator.SOFT_DELETE_VALUE_DELETED;
    public static final Value HIDDEN_VALUE = ElementIterator.HIDDEN_VALUE;
    public static final Text CF_PROPERTY_HIDDEN = ElementIterator.CF_PROPERTY_HIDDEN;
    public static final Value HIDDEN_VALUE_DELETED = ElementIterator.HIDDEN_VALUE_DELETED;
    public static final Value ADD_ADDITIONAL_VISIBILITY_VALUE = ElementIterator.ADDITIONAL_VISIBILITY_VALUE;
    public static final Value ADD_ADDITIONAL_VISIBILITY_VALUE_DELETED = ElementIterator.ADDITIONAL_VISIBILITY_VALUE_DELETED;
    public static final Value SIGNAL_VALUE_DELETED = ElementIterator.SIGNAL_VALUE_DELETED;
    public static final Text DELETE_ROW_COLUMN_FAMILY = ElementIterator.DELETE_ROW_COLUMN_FAMILY;
    public static final Text DELETE_ROW_COLUMN_QUALIFIER = ElementIterator.DELETE_ROW_COLUMN_QUALIFIER;
    public static final Text CF_SOFT_DELETE = ElementIterator.CF_SOFT_DELETE;
    public static final Text CQ_SOFT_DELETE = ElementIterator.CQ_SOFT_DELETE;
    public static final Text CF_HIDDEN = ElementIterator.CF_HIDDEN;
    public static final Text CQ_HIDDEN = ElementIterator.CQ_HIDDEN;
    public static final Text CF_ADDITIONAL_VISIBILITY = ElementIterator.CF_ADDITIONAL_VISIBILITY;
    public static final Text METADATA_COLUMN_FAMILY = ElementIterator.METADATA_COLUMN_FAMILY;
    public static final Text METADATA_COLUMN_QUALIFIER = ElementIterator.METADATA_COLUMN_QUALIFIER;

    private final Graph graph;
    private final String id;
    private Visibility visibility;
    private final long timestamp;
    private final FetchHints fetchHints;
    private final Set<Visibility> hiddenVisibilities;
    private final Set<String> additionalVisibilities;

    private final PropertyCollection properties;
    private final ImmutableSet<String> extendedDataTableNames;
    private ConcurrentSkipListSet<PropertyDeleteMutation> propertyDeleteMutations;
    private ConcurrentSkipListSet<PropertySoftDeleteMutation> propertySoftDeleteMutations;
    private final User user;

    protected AccumuloElement(
        Graph graph,
        String id,
        Visibility visibility,
        Iterable<Property> properties,
        Iterable<PropertyDeleteMutation> propertyDeleteMutations,
        Iterable<PropertySoftDeleteMutation> propertySoftDeleteMutations,
        Iterable<Visibility> hiddenVisibilities,
        Iterable<String> additionalVisibilities,
        ImmutableSet<String> extendedDataTableNames,
        long timestamp,
        FetchHints fetchHints,
        User user
    ) {
        this.graph = graph;
        this.id = id;
        this.visibility = visibility;
        this.timestamp = timestamp;
        this.fetchHints = fetchHints;
        this.properties = new PropertyCollection();
        this.extendedDataTableNames = extendedDataTableNames;
        this.user = user;

        ImmutableSet.Builder<Visibility> hiddenVisibilityBuilder = new ImmutableSet.Builder<>();
        if (hiddenVisibilities != null) {
            for (Visibility v : hiddenVisibilities) {
                hiddenVisibilityBuilder.add(v);
            }
        }
        this.hiddenVisibilities = hiddenVisibilityBuilder.build();
        this.additionalVisibilities = Sets.newHashSet(additionalVisibilities);
        updatePropertiesInternal(properties, propertyDeleteMutations, propertySoftDeleteMutations);
    }

    @Override
    public AccumuloGraph getGraph() {
        return (AccumuloGraph) graph;
    }

    @SuppressWarnings("unchecked")
    protected <TElement extends Element> void saveExistingElementMutation(ExistingElementMutation<TElement> mutation, User user) {
        if (mutation instanceof EdgeMutation) {
            getGraph().elementMutationBuilder.saveEdgeMutation((EdgeMutation) mutation, IncreasingTime.currentTimeMillis(), user);
        } else {
            getGraph().elementMutationBuilder.saveVertexMutation((ElementMutation<Vertex>) mutation, IncreasingTime.currentTimeMillis(), user);
        }
    }

    @Override
    public Stream<HistoricalEvent> getHistoricalEvents(
        HistoricalEventId after,
        HistoricalEventsFetchHints fetchHints,
        User user
    ) {
        return getGraph().getHistoricalEvents(
            Lists.newArrayList(new ElementId(ElementType.getTypeFromElement(this), getId())),
            after,
            fetchHints,
            user
        );
    }

    @Override
    public QueryableIterable<ExtendedDataRow> getExtendedData(String tableName, FetchHints fetchHints) {
        return new ExtendedDataQueryableIterable(
            getGraph(),
            this,
            tableName,
            toIterable(getGraph().getExtendedData(
                ElementType.getTypeFromElement(this),
                getId(),
                tableName,
                fetchHints,
                getUser()
            ))
        );
    }

    @Override
    public Object getPropertyValue(String key, String name, int index) {
        if (isInternalPropertyName(name)) {
            return getProperty(name).getValue();
        }
        getFetchHints().assertPropertyIncluded(name);
        Property property = this.properties.getProperty(key, name, index);
        if (property == null) {
            return null;
        }
        return property.getValue();
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    protected void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    @Override
    public Iterable<Property> getProperties() {
        if (!getFetchHints().isIncludeProperties()) {
            throw new VertexiumMissingFetchHintException(getFetchHints(), "includeProperties");
        }
        return this.properties.getProperties();
    }

    public Iterable<PropertyDeleteMutation> getPropertyDeleteMutations() {
        return this.propertyDeleteMutations;
    }

    public Iterable<PropertySoftDeleteMutation> getPropertySoftDeleteMutations() {
        return this.propertySoftDeleteMutations;
    }

    // this method differs setProperties in that it only updates the in memory representation of the properties
    protected void updatePropertiesInternal(
        Iterable<Property> properties,
        Iterable<PropertyDeleteMutation> propertyDeleteMutations,
        Iterable<PropertySoftDeleteMutation> propertySoftDeleteMutations
    ) {
        if (propertyDeleteMutations != null) {
            this.propertyDeleteMutations = new ConcurrentSkipListSet<>();
            for (PropertyDeleteMutation propertyDeleteMutation : propertyDeleteMutations) {
                removePropertyInternal(
                    propertyDeleteMutation.getKey(),
                    propertyDeleteMutation.getName(),
                    propertyDeleteMutation.getVisibility()
                );
                this.propertyDeleteMutations.add(propertyDeleteMutation);
            }
        }
        if (propertySoftDeleteMutations != null) {
            this.propertySoftDeleteMutations = new ConcurrentSkipListSet<>();
            for (PropertySoftDeleteMutation propertySoftDeleteMutation : propertySoftDeleteMutations) {
                removePropertyInternal(
                    propertySoftDeleteMutation.getKey(),
                    propertySoftDeleteMutation.getName(),
                    propertySoftDeleteMutation.getVisibility()
                );
                this.propertySoftDeleteMutations.add(propertySoftDeleteMutation);
            }
        }

        for (Property property : properties) {
            addPropertyInternal(property);
        }
    }

    protected void removePropertyInternal(String key, String name, Visibility visibility) {
        Property property = getProperty(key, name, visibility);
        if (property != null) {
            this.properties.removeProperty(property);
        }
    }

    protected void addPropertyInternal(Property property) {
        if (property.getKey() == null) {
            throw new IllegalArgumentException("key is required for property");
        }
        Property existingProperty = getProperty(property.getKey(), property.getName(), property.getVisibility());
        if (existingProperty == null) {
            this.properties.addProperty(property);
        } else {
            if (existingProperty instanceof MutableProperty) {
                ((MutableProperty) existingProperty).update(property);
            } else {
                throw new VertexiumException("Could not update property of type: " + existingProperty.getClass().getName());
            }
        }
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public Iterable<Visibility> getHiddenVisibilities() {
        return hiddenVisibilities;
    }

    @Override
    public ImmutableSet<String> getAdditionalVisibilities() {
        return ImmutableSet.copyOf(additionalVisibilities);
    }

    @Override
    public ImmutableSet<String> getExtendedDataTableNames() {
        if (!getFetchHints().isIncludeExtendedDataTableNames()) {
            throw new VertexiumMissingFetchHintException(getFetchHints(), "includeExtendedDataTableNames");
        }

        return extendedDataTableNames;
    }

    @Override
    public FetchHints getFetchHints() {
        return fetchHints;
    }

    @Override
    protected Iterable<Property> internalGetProperties(String key, String name) {
        getFetchHints().assertPropertyIncluded(name);
        return this.properties.getProperties(key, name);
    }
}
