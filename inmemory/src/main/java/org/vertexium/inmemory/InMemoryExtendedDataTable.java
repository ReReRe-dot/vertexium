package org.vertexium.inmemory;

import com.google.common.collect.ImmutableSet;
import org.vertexium.*;

public abstract class InMemoryExtendedDataTable {
    public abstract ImmutableSet<String> getTableNames(
        ElementType elementType,
        String elementId,
        FetchHints fetchHints,
        User user
    );

    public abstract Iterable<? extends ExtendedDataRow> getTable(
        ElementType elementType,
        String elementId,
        String tableName,
        FetchHints fetchHints,
        User user
    );

    public abstract void addData(
        InMemoryGraph graph,
        ExtendedDataRowId rowId,
        String column,
        String key,
        Object value,
        long timestamp,
        Visibility visibility,
        User user
    );

    public abstract void remove(ExtendedDataRowId id);

    public abstract void removeColumn(ExtendedDataRowId extendedDataRowId, String columnName, String key, Visibility visibility);

    public abstract void addAdditionalVisibility(
        ExtendedDataRowId extendedDataRowId,
        String additionalVisibility
    );

    public abstract void deleteAdditionalVisibility(
        ExtendedDataRowId extendedDataRowId,
        String additionalVisibility
    );
}
