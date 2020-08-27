/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.randomcutforest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;

import com.amazon.randomcutforest.returntypes.ConvergingAccumulator;
import com.amazon.randomcutforest.tree.ITree;
import com.amazon.randomcutforest.tree.IUpdatableTree;
import com.amazon.randomcutforest.tree.RandomCutTree;

public abstract class GenericForestTraversalExecutor<P> {

    protected final IUpdateCoordinator<P> updateCoordinator;
    protected final ArrayList<IUpdatableTree<P>> trees;

    protected GenericForestTraversalExecutor(IUpdateCoordinator<P> updateCoordinator,
            ArrayList<IUpdatableTree<P>> trees) {
        this.updateCoordinator = updateCoordinator;
        this.trees = trees;
    }

    public long getTotalUpdates() {
        return updateCoordinator.getTotalUpdates();
    }

    /**
     * Update the forest with the given point. The point is submitted to each
     * sampler in the forest. If the sampler accepts the point, the point is
     * submitted to the update method in the corresponding Random Cut Tree.
     *
     * @param point The point used to update the forest.
     */
    public void update(double[] point) {
        double[] pointCopy = cleanCopy(point);
        P updateInput = updateCoordinator.initUpdate(pointCopy);
        List<P> results = update(updateInput);
        updateCoordinator.completeUpdate(results);
    }

    /**
     * Internal update method which submits the given input value to
     * {@link IUpdatableTree#update} for each tree managed by this executor.
     *
     * @param updateInput Input value that will be submitted to the update method
     *                    for each tree.
     */
    protected abstract List<P> update(P updateInput);

    /**
     * Visit each of the trees in the forest and combine the individual results into
     * an aggregate result. A visitor is constructed for each tree using the visitor
     * factory, and then submitted to
     * {@link RandomCutTree#traverseTree(double[], Visitor)}. The results from all
     * the trees are combined using the accumulator and then transformed using the
     * finisher before being returned.
     *
     * @param point          The point that defines the traversal path.
     * @param visitorFactory A factory method which is invoked for each tree to
     *                       construct a visitor.
     * @param accumulator    A function that combines the results from individual
     *                       trees into an aggregate result.
     * @param finisher       A function called on the aggregate result in order to
     *                       produce the final result.
     * @param <R>            The visitor result type. This is the type that will be
     *                       returned after traversing each individual tree.
     * @param <S>            The final type, after any final normalization at the
     *                       forest level.
     * @return The aggregated and finalized result after sending a visitor through
     *         each tree in the forest.
     */
    public abstract <R, S> S traverseForest(double[] point, Function<ITree<?>, Visitor<R>> visitorFactory,
            BinaryOperator<R> accumulator, Function<R, S> finisher);

    /**
     * Visit each of the trees in the forest and combine the individual results into
     * an aggregate result. A visitor is constructed for each tree using the visitor
     * factory, and then submitted to
     * {@link RandomCutTree#traverseTree(double[], Visitor)}. The results from
     * individual trees are collected using the {@link java.util.stream.Collector}
     * and returned. Trees are visited in parallel using
     * {@link java.util.Collection#parallelStream()}.
     *
     * @param point          The point that defines the traversal path.
     * @param visitorFactory A factory method which is invoked for each tree to
     *                       construct a visitor.
     * @param collector      A collector used to aggregate individual tree results
     *                       into a final result.
     * @param <R>            The visitor result type. This is the type that will be
     *                       returned after traversing each individual tree.
     * @param <S>            The final type, after any final normalization at the
     *                       forest level.
     * @return The aggregated and finalized result after sending a visitor through
     *         each tree in the forest.
     */
    public abstract <R, S> S traverseForest(double[] point, Function<ITree<?>, Visitor<R>> visitorFactory,
            Collector<R, ?, S> collector);

    /**
     * Visit each of the trees in the forest sequentially and combine the individual
     * results into an aggregate result. A visitor is constructed for each tree
     * using the visitor factory, and then submitted to
     * {@link RandomCutTree#traverseTree(double[], Visitor)}. The results from all
     * the trees are combined using the {@link ConvergingAccumulator}, and the
     * method stops visiting trees after convergence is reached. The result is
     * transformed using the finisher before being returned.
     *
     * @param point          The point that defines the traversal path.
     * @param visitorFactory A factory method which is invoked for each tree to
     *                       construct a visitor.
     * @param accumulator    An accumulator that combines the results from
     *                       individual trees into an aggregate result and checks to
     *                       see if the result can be returned without further
     *                       processing.
     * @param finisher       A function called on the aggregate result in order to
     *                       produce the final result.
     * @param <R>            The visitor result type. This is the type that will be
     *                       returned after traversing each individual tree.
     * @param <S>            The final type, after any final normalization at the
     *                       forest level.
     * @return The aggregated and finalized result after sending a visitor through
     *         each tree in the forest.
     */
    public abstract <R, S> S traverseForest(double[] point, Function<ITree<?>, Visitor<R>> visitorFactory,
            ConvergingAccumulator<R> accumulator, Function<R, S> finisher);

    /**
     * Visit each of the trees in the forest and combine the individual results into
     * an aggregate result. A multi-visitor is constructed for each tree using the
     * visitor factory, and then submitted to
     * {@link RandomCutTree#traverseTreeMulti(double[], MultiVisitor)}. The results
     * from all the trees are combined using the accumulator and then transformed
     * using the finisher before being returned.
     *
     * @param point          The point that defines the traversal path.
     * @param visitorFactory A factory method which is invoked for each tree to
     *                       construct a multi-visitor.
     * @param accumulator    A function that combines the results from individual
     *                       trees into an aggregate result.
     * @param finisher       A function called on the aggregate result in order to
     *                       produce the final result.
     * @param <R>            The visitor result type. This is the type that will be
     *                       returned after traversing each individual tree.
     * @param <S>            The final type, after any final normalization at the
     *                       forest level.
     * @return The aggregated and finalized result after sending a visitor through
     *         each tree in the forest.
     */
    public abstract <R, S> S traverseForestMulti(double[] point, Function<ITree<?>, MultiVisitor<R>> visitorFactory,
            BinaryOperator<R> accumulator, Function<R, S> finisher);

    /**
     * Visit each of the trees in the forest and combine the individual results into
     * an aggregate result. A multi-visitor is constructed for each tree using the
     * visitor factory, and then submitted to
     * {@link RandomCutTree#traverseTreeMulti(double[], MultiVisitor)}. The results
     * from individual trees are collected using the
     * {@link java.util.stream.Collector} and returned. Trees are visited in
     * parallel using {@link java.util.Collection#parallelStream()}.
     *
     * @param point          The point that defines the traversal path.
     * @param visitorFactory A factory method which is invoked for each tree to
     *                       construct a visitor.
     * @param collector      A collector used to aggregate individual tree results
     *                       into a final result.
     * @param <R>            The visitor result type. This is the type that will be
     *                       returned after traversing each individual tree.
     * @param <S>            The final type, after any final normalization at the
     *                       forest level.
     * @return The aggregated and finalized result after sending a visitor through
     *         each tree in the forest.
     */
    public abstract <R, S> S traverseForestMulti(double[] point, Function<ITree<?>, MultiVisitor<R>> visitorFactory,
            Collector<R, ?, S> collector);

    /**
     * Returns a clean deep copy of the point.
     *
     * Current clean-ups include changing negative zero -0.0 to positive zero 0.0.
     *
     * @param point The original data point.
     * @return a clean deep copy of the original point.
     */
    protected double[] cleanCopy(double[] point) {
        double[] pointCopy = Arrays.copyOf(point, point.length);
        for (int i = 0; i < point.length; i++) {
            if (pointCopy[i] == 0.0) {
                pointCopy[i] = 0.0;
            }
        }
        return pointCopy;
    }
}
