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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pruning proof based according essential conclusion criteria
 * 
 * @author Marouane Nadir
 */

public class PrunedProofEssential<C,I extends Inference<? extends C>>
		extends DelegatingProof<I, Proof<? extends I>>
		implements Proof<I>, Producer<I> {

	private final Map<Object, I> expanded_ = new HashMap<Object, I>();
	private Set<C> essential;
	private Set<Object> derivableConclusion;
	private Set<C> axiomsFromOntology;

	public PrunedProofEssential(Proof<? extends I> delegate, C goal) {
		super(delegate);
		axiomsFromOntology=Proofs.getAxiomsOntology(delegate, goal);
		essential = Proofs.getEssentialAxioms(delegate, goal,axiomsFromOntology);
		//used only to check the difference between essential and derivable conclusion sets
		derivableConclusion=essential.stream().collect(Collectors.toSet());	
		Proofs.expand(derivableConclusion,Proofs.removeAssertedInferences(delegate,axiomsFromOntology),
				goal, this);
	}

	@Override
	public void produce(I inf) {
		expanded_.put(inf.getConclusion(), inf);
	}

	@Override
	public Collection<? extends I> getInferences(Object conclusion) {
		I inf = expanded_.get(conclusion);
		if (inf == null) {
			return super.getInferences(conclusion);
		}
		// else
		return Collections.singleton(inf);
	}

	public Map<Object, I> getExpanded_() {
		return expanded_;
	}
	
	public Set<C> getEssential() {
		return essential;
	}
	
	public Set<Object> getDerivable() {
		return derivableConclusion;
	}

}