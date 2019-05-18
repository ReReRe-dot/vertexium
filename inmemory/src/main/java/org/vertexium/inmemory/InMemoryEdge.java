package org.vertexium.inmemory;

import org.vertexium.*;
import org.vertexium.inmemory.mutations.AlterEdgeLabelMutation;
import org.vertexium.inmemory.mutations.EdgeSetupMutation;
import org.vertexium.mutation.ExistingEdgeMutation;

public class InMemoryEdge extends InMemoryElement<InMemoryEdge> implements Edge {
    private final EdgeSetupMutation edgeSetupMutation;

    public InMemoryEdge(
        InMemoryGraph graph,
        String id,
        InMemoryTableEdge inMemoryTableElement,
        FetchHints fetchHints,
        Long endTime,
        User user
    ) {
        super(graph, id, inMemoryTableElement, fetchHints, endTime, user);
        edgeSetupMutation = inMemoryTableElement.findLastMutation(EdgeSetupMutation.class);
    }

    @Override
    public ElementType getElementType() {
        return ElementType.EDGE;
    }

    @Override
    public String getLabel() {
        return getInMemoryTableElement().findLastMutation(AlterEdgeLabelMutation.class).getNewEdgeLabel();
    }

    @Override
    public String getVertexId(Direction direction) {
        switch (direction) {
            case IN:
                return edgeSetupMutation.getInVertexId();
            case OUT:
                return edgeSetupMutation.getOutVertexId();
            default:
                throw new IllegalArgumentException("Unexpected direction: " + direction);
        }
    }

    @Override
    public EdgeVertices getVertices(FetchHints fetchHints, Authorizations authorizations) {
        return new EdgeVertices(
            getVertex(Direction.OUT, authorizations),
            getVertex(Direction.IN, authorizations)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExistingEdgeMutation prepareMutation() {
        return new ExistingEdgeMutation(this) {
            @Override
            public Edge save(Authorizations authorizations) {
                User user = authorizations.getUser();
                return saveEdge(user);
            }

            @Override
            public String save(User user) {
                return saveEdge(user).getId();
            }

            private Edge saveEdge(User user) {
                getGraph().getElementMutationBuilder().saveExistingElementMutation(this, user);
                return getElement();
            }
        };
    }
}
