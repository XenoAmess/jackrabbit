/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query.lucene;

import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.query.QueryResult;
import javax.jcr.query.qom.PropertyValue;
import javax.jcr.query.qom.QueryObjectModelConstants;

import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.query.PropertyTypeRegistry;
import org.apache.jackrabbit.core.query.lucene.constraint.Constraint;
import org.apache.jackrabbit.core.query.lucene.constraint.ConstraintBuilder;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.commons.name.PathFactoryImpl;
import org.apache.jackrabbit.spi.commons.query.qom.BindVariableValueImpl;
import org.apache.jackrabbit.spi.commons.query.qom.ColumnImpl;
import org.apache.jackrabbit.spi.commons.query.qom.DefaultTraversingQOMTreeVisitor;
import org.apache.jackrabbit.spi.commons.query.qom.OrderingImpl;
import org.apache.jackrabbit.spi.commons.query.qom.QueryObjectModelTree;

/**
 * <code>QueryObjectModelImpl</code>...
 */
public class QueryObjectModelImpl extends AbstractQueryImpl {

    /**
     * The query object model tree.
     */
    private final QueryObjectModelTree qomTree;

    /**
     * Creates a new query instance from a query string.
     *
     * @param session the session of the user executing this query.
     * @param itemMgr the item manager of the session executing this query.
     * @param index   the search index.
     * @param propReg the property type registry.
     * @param qomTree the query object model tree.
     */
    public QueryObjectModelImpl(SessionImpl session,
                                ItemManager itemMgr,
                                SearchIndex index,
                                PropertyTypeRegistry propReg,
                                QueryObjectModelTree qomTree) {
        super(session, itemMgr, index, propReg);
        this.qomTree = qomTree;
        extractBindVariableNames();
    }

    /**
     * Returns <code>true</code> if this query node needs items under
     * /jcr:system to be queried.
     *
     * @return <code>true</code> if this query node needs content under
     *         /jcr:system to be queried; <code>false</code> otherwise.
     */
    public boolean needsSystemTree() {
        // TODO: analyze QOM tree
        return true;
    }

    //-------------------------< ExecutableQuery >------------------------------

    /**
     * Executes this query and returns a <code>{@link QueryResult}</code>.
     *
     * @param offset the offset in the total result set
     * @param limit  the maximum result size
     * @return a <code>QueryResult</code>
     * @throws RepositoryException if an error occurs
     */
    public QueryResult execute(long offset, long limit)
            throws RepositoryException {

        LuceneQueryFactory factory = new LuceneQueryFactoryImpl(session,
                index.getSortComparatorSource(),
                index.getContext().getHierarchyManager(),
                index.getNamespaceMappings(), index.getTextAnalyzer(),
                index.getSynonymProvider(), index.getIndexFormatVersion());

        MultiColumnQuery query = factory.create(qomTree.getSource());

        if (qomTree.getConstraint() != null) {
            Constraint c = ConstraintBuilder.create(qomTree.getConstraint(),
                    getBindVariableValues(), qomTree.getSource().getSelectors(),
                    factory, session.getValueFactory());
            query = new FilterMultiColumnQuery(query, c);
        }


        ColumnImpl[] columns = qomTree.getColumns();
        Name[] selectProps = new Name[columns.length];
        for (int i = 0; i < columns.length; i++) {
            selectProps[i] = columns[i].getPropertyQName();
        }
        OrderingImpl[] orderings = qomTree.getOrderings();
        // TODO: there are many kinds of DynamicOperand that can be ordered by
        Path[] orderProps = new Path[orderings.length];
        boolean[] orderSpecs = new boolean[orderings.length];
        for (int i = 0; i < orderings.length; i++) {
            orderSpecs[i] = 
                QueryObjectModelConstants.JCR_ORDER_ASCENDING.equals(
                        orderings[i].getOrder());
            if (orderings[i].getOperand() instanceof PropertyValue) {
                PropertyValue pv = (PropertyValue) orderings[i].getOperand();
                orderProps[i] = PathFactoryImpl.getInstance().create(pv.getPropertyName());
            } else {
                throw new UnsupportedRepositoryOperationException("order by with" +
                        orderings[i].getOperand() + " not yet implemented");
            }
        }
        return new MultiColumnQueryResult(index, itemMgr,
                session, session.getAccessManager(),
                // TODO: spell suggestion missing
                this, query, null, selectProps, orderProps, orderSpecs,
                getRespectDocumentOrder(), offset, limit);
    }

    //--------------------------< internal >------------------------------------

    /**
     * Extracts all {@link BindVariableValueImpl} from the {@link #qomTree}
     * and adds it to the set of known variable names.
     */
    private void extractBindVariableNames() {
        try {
            qomTree.accept(new DefaultTraversingQOMTreeVisitor() {
                public Object visit(BindVariableValueImpl node, Object data) {
                    addVariableName(node.getBindVariableQName());
                    return data;
                }
            }, null);
        } catch (Exception e) {
            // will never happen
        }
    }
}
