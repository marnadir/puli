/*-
 * #%L
 * Proof Utility Library
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2017 Live Ontologies Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.liveontologies.puli;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.elk.reasoner.indexing.model.IndexedAxiom;
import org.semanticweb.owlapi.debugging.JustificationMap;

/**
 * A collection of static methods for working with {@link Proof}s
 * 
 * @author Yevgeny Kazakov
 *
 */
public class Proofs {

	@SuppressWarnings("rawtypes")
	public static DynamicProof EMPTY_PROOF = new EmptyProof();

	/**
	 * @return a {@link DynamicProof} that has no inference, i.e.,
	 *         {@link DynamicProof#getInferences(Object)} is always the empty
	 *         set. This proof never changes so if a
	 *         {@link DynamicProof.ChangeListener} is added, it does not receive
	 *         any notifications.
	 */
	@SuppressWarnings("unchecked")
	public static <I extends Inference<?>> DynamicProof<I> emptyProof() {
		return (DynamicProof<I>) EMPTY_PROOF;
	}

	/**
	 * @param proof
	 * @param conclusion
	 * @return {@code true} if the given conclusion is derivable in the given
	 *         {@link Proof}, i.e., there exists an sequence of conclusions
	 *         ending with the given conclusion, such that for each conclusion
	 *         there exists an inference in {@link Proof#getInferences} that has
	 *         as premises only conclusions that appear before in this sequence.
	 */
	public static boolean isDerivable(Proof<?> proof, Object conclusion) {
		return new InferenceDerivabilityChecker<Object, Inference<?>>(proof)
				.isDerivable(conclusion);
		// alternatively:
		// return ProofNodes.isDerivable(ProofNodes.create(proof, conclusion));
	}

	/**
	 * @param proofs
	 * @return the union of the given the {@link Proof}s, i.e., a {@link Proof}
	 *         that for each conclusion returns the union of inferences returned
	 *         by the proofs in the argument
	 */
	public static <I extends Inference<?>> Proof<I> union(
			final Iterable<? extends Proof<? extends I>> proofs) {
		return new ProofUnion<I>(proofs);
	}

	/**
	 * @param proofs
	 * @return the union of the given the {@link Proof}s, i.e., a {@link Proof}
	 *         that for each conclusion returns the union of inferences returned
	 *         by the proofs in the argument
	 */
	@SafeVarargs
	public static <I extends Inference<?>> Proof<I> union(
			final Proof<? extends I>... proofs) {
		return new ProofUnion<I>(proofs);
	}

	/**
	 * @param proof
	 * @return the {@link Proof} that has all inferences of the given
	 *         {@link Proof} except for the asserted inferences, i.e., all
	 *         inferences for which {@link Inferences#isAsserted(Inference)}
	 *         returns {@code false}.
	 */
	public static <I extends Inference<?>> Proof<I> removeAssertedInferences(
			final Proof<? extends I> proof) {
		return removeAssertedInferences(proof, Collections.emptySet());
	}

	/**
	 * @param proof
	 * @param assertedConclusions
	 * @return the {@link Proof} that has all inferences of the given
	 *         {@link Proof} except for the asserted inferences (inferences for
	 *         which {@link Inferences#isAsserted(Inference)} returns
	 *         {@code false}), whose conclusions are not in the given set.
	 */
	public static <I extends Inference<?>> Proof<I> removeAssertedInferences(
			final Proof<? extends I> proof, final Set<?> assertedConclusions) {
		return new RemoveAssertedProof<I>(proof, assertedConclusions);
	}
	
	/**
	 * @param proof
	 * @return {@link DynamicProof} that caches all
	 *         {@link DynamicProof#getInferences(Object)} requests of the input
	 *         {@link DynamicProof}, until the input proof changes
	 */
	public static <I extends Inference<?>> DynamicProof<I> cache(
			DynamicProof<? extends I> proof) {
		return new CachingProof<I>(proof);
	}

	/**
	 * Recursively enumerates all inferences of the given {@link Proof} starting
	 * from the inferences for the given goal conclusion and then proceeding to
	 * the inferences of their premises. The encountered inferences are reported
	 * using the provided {@link Producer} by calling {@link Producer#produce}.
	 * The inferences for each conclusion are enumerated only once even if the
	 * conclusion appears as premise in several inferences.
	 * 
	 * @param proof
	 * @param goal
	 * @param producer
	 * @return the set of all conclusions for which the inferences were
	 *         enumerated
	 */
	public static <C, I extends Inference<? extends C>> Set<C> unfoldRecursively(
			Proof<? extends I> proof, C goal, Producer<? super I> producer) {
		Set<C> result = new HashSet<C>();
		Queue<C> toExpand = new ArrayDeque<C>();
		result.add(goal);
		toExpand.add(goal);
		for (;;) {
			C next = toExpand.poll();
			if (next == null) {
				break;
			}
			for (I inf : proof.getInferences(next)) {
				producer.produce(inf);
				for (C premise : inf.getPremises()) {
					if (result.add(premise)) {
						toExpand.add(premise);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Recursively enumerates all inferences of the given {@link Proof} starting
	 * from the inferences for the given goal conclusion and then proceeding to
	 * the inferences of their premises.The encountered inferences are reported
	 * using the provided {@link Producer} by calling {@link Producer#produce}.
	 * The inferences for each conclusion are enumerated only once even if the
	 * conclusion appears as premise in several inferences. The method returns
	 * the conclusions for which the inferences were enumerated which 
	 * are contained in the ontology
	 * 
	 * @param proof
	 * @param goal
	 * @return the set of all conclusions, contained in the ontology, for which the inferences were
	 *         enumerated
	 */
	
	public static <C, I extends Inference<? extends C>> Set<C> getAxiomsOntology(
			Proof<? extends I> proof, C goal) {
		Set<C> conclusions = new HashSet<C>();
		Set<C> result = new HashSet<C>();
		Queue<C> toExpand = new ArrayDeque<C>();
		conclusions.add(goal);
		toExpand.add(goal);
		for (;;) {
			C next = toExpand.poll();
			if (next == null) {
				break;
			}
			for (I inf : proof.getInferences(next)) {
				if(inf.getPremises().size()==0) {
					result.add(inf.getConclusion());
				}
				for (C premise : inf.getPremises()) {
					if (conclusions.add(premise)) {
						toExpand.add(premise);
					}
				}
			}
		}
		return result;
	}
	
	
	private static <C, I extends Inference<? extends C>> Set<C> unfoldInfs(
			Proof<? extends I> proof, C goal,Set<C> just ) {
		Set<C> result = new HashSet<C>();
		Set<C> newJust = new HashSet<C>();
		newJust.addAll(just);		
		Queue<C> toExpand = new ArrayDeque<C>();
		result.add(goal);
		toExpand.add(goal);
		for (;;) {
			C next = toExpand.poll();
			if (next == null) {
				break;
			}
			for (I inf : proof.getInferences(next)) {
				for (C premise : inf.getPremises()) {
					if(just.contains(premise)) {
						newJust.addAll(inf.getPremises());
					}
					if (result.add(premise)) {
						toExpand.add(premise);
					}
				}
			}
		}

		return newJust;
	}
	
	/**
	 * Convert the justifications which are given as OWL axioms to ELK axioms
	 * @param proof
	 * @param goal
	 * @param ontology
	 * @param justifications
	 * @return justifications as ELK axioms
	 */
	
	public static <C, I extends Inference<? extends C>> Set<C> convertElkJust(
			Proof<? extends I> proof, C goal,Set<C> ontology,Set<C> justifications) {
		Set<C> result = new HashSet<C>();
		for(C axiom:ontology) {
			if(axiom instanceof IndexedAxiom) {
				if(justifications.contains(((IndexedAxiom) axiom).getOriginalAxiom())) {
					result.add(axiom);
				}
			}
			
		}
		result = unfoldInfs(proof, goal, result);
		return result;
	}
	
	
	/**
	 * @param proof
	 * @param goal
	 * @return the number of inferences in the proof that is used for deriving
	 *         the given goal
	 */
	public static int countInferences(Proof<?> proof, Object goal) {
		final int[] counter = { 0 };
		unfoldRecursively(proof, goal, new Producer<Inference<?>>() {
			@Override
			public void produce(Inference<?> object) {
				counter[0]++;
			}
		});
		return counter[0];
	}

	/**
	 * @param proof
	 * @param goal
	 * @return the set of axioms from an the ontology without which the goal would not be
	 *         derivable using the given inferences; i.e., every derivation
	 *         using the inferences must start with an inference with conclusion contained in the set
	 */
	public static <C, I extends Inference<? extends C>> Set<C> getEssentialAxioms(Proof<? extends I> proof,
			C goal,Set<C> ontology) {
		
		Set<C> result = new HashSet<C>();
		for (C candidate : ontology) {
			DerivabilityCheckerWithBlocking<C,I> checker = new AxiomDerivabilityChecker<C,I>(proof);
			checker.block(candidate);
			if (!checker.isDerivable(goal)) {
				result.add(candidate);
			}
			checker.unblock(candidate);
		}

		return result;
	}

	/**
	 * Adds to the set of conclusions all conclusions that are derived from them
	 * using the inferences of the given proof that can be used for proving the
	 * given goal; produces the applied inferences using the given producer
	 * 
	 * @param derivable
	 * @param proof
	 * @param goal
	 * @param producer
	 */
	public static <C, I extends Inference<? extends C>> void expand(
			Set<C> derivable, Proof<? extends I> proof, C goal,
			Producer<? super I> producer) {
		InferenceExpander.expand(derivable, proof, goal, producer);
	}
	
	
	/**
	 * Detect all inferences which create a cycle derivation for proving the
	 * given goal; produces the applied inferences using the given producer
	 * 
	 * @param derivable
	 * @param proof
	 * @param goal
	 * @param producer
	 */	
	public static <C, I extends Inference<? extends C>> void detectCycle(Proof<? extends I> proof, C goal,
			Producer<? super I> producer,Set<I> infCycle_) {
		InferenceCycleChecker.detectCycle(proof, goal, producer,infCycle_);
	}

	/**
	 * @param proof
	 * @param goal
	 * @return a proof obtained from the given proofs by removing some
	 *         inferences (according essential axiom) that do not have effect on the derivation relation
	 *         between the asserted conclusions in the proof (derived by
	 *         asserted inferences) and the goal conclusion; i.e., if the goal
	 *         conclusion was derivable from some subset of asserted conclusions
	 *         using original inferences, then it is also derivable using the
	 *         returned proof
	 * @see Inferences#isAsserted(Inference)
	 */
	public static <C,I extends Inference<? extends C>> Proof<I> pruneEssential(
			Proof<? extends I> proof, C goal) {
		return new PrunedProofEssential<C,I>(proof, goal);
	}

	/**
	 * @param proof
	 * @param goal
	 * @return a proof obtained from the given proofs by removing cycles
	 *         inferences that do not have effect on the derivation relation
	 *         between the asserted conclusions in the proof (derived by
	 *         asserted inferences) and the goal conclusion; i.e., if the goal
	 *         conclusion was derivable from some subset of asserted conclusions
	 *         using original inferences, then it is also derivable using the
	 *         returned proof
	 * @see Inferences#isAsserted(Inference)
	 */
	
	public static <C, I extends Inference<? extends C>> Proof<I> pruneCycle
	(Proof<I> proof, C goal) {
		return new PrunedProofCycle<C,I>(proof, goal);
	}
	
	/**
	 * @param proof
	 * @param goal
	 * @return a proof obtained from the given proofs by removing any 
	 *         inferences that any premise is not derivable from 
	 *         the justifications, i.e., if the goal
	 *         conclusion was derivable from some subset of asserted conclusions
	 *         using original inferences, then it is also derivable using the
	 *         returned proof
	 * @see Inferences#isAsserted(Inference)
	 */
	
	public static <C, I extends Inference<? extends C>> Proof<I> pruneFromJustifications
	(Proof<? extends I> proof_, C query,Set<Object> justifications,Proof<? extends I> proofType) {
		return new PrunedProofJust<I>(proof_, query,justifications,proofType);
	}
	
	/**
	 * Recursively prints all inferences for the derived goal and the premises
	 * of such inferences to the standard output using ASCII characters. Due to
	 * potential cycles, inferences for every conclusion are printed only once
	 * upon their first occurrence in the proof. Every following occurrence of
	 * the same conclusion is labeled by {@code *}.
	 * 
	 * @param proof
	 *            the {@link Proof} from which to take the inferences
	 * @param goal
	 *            the conclusion starting from which the inferences are printed
	 */
	public static void print(Proof<?> proof, Object goal) {
		try {
			ProofPrinter.print(proof, goal);
		} catch (IOException e) {
			throw new RuntimeException("Exception while printing the proof", e);
		}

	}

}
