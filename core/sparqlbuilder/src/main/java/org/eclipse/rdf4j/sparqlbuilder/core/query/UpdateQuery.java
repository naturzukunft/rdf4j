/*******************************************************************************
 Copyright (c) 2018 Eclipse RDF4J contributors.
 All rights reserved. This program and the accompanying materials
 are made available under the terms of the Eclipse Distribution License v1.0
 which accompanies this distribution, and is available at
 http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sparqlbuilder.core.query;

import static org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri;

import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.sparqlbuilder.core.Base;
import org.eclipse.rdf4j.sparqlbuilder.core.Prefix;
import org.eclipse.rdf4j.sparqlbuilder.core.PrefixDeclarations;
import org.eclipse.rdf4j.sparqlbuilder.core.QueryElement;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.TriplesTemplate;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphName;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.util.SparqlBuilderUtils;

/**
 * A SPARQL Update query
 *
 * @param <T> The type of update query. Used to support fluency.
 *
 * @see <a href="https://www.w3.org/TR/sparql11-update/"> SPARQL Update Query</a>
 */
@SuppressWarnings("unchecked")
abstract class UpdateQuery<T extends UpdateQuery<T>> implements QueryElement {
	private Optional<Base> base = Optional.empty();
	private Optional<PrefixDeclarations> prefixes = Optional.empty();

	UpdateQuery() {
	}

	/**
	 * Set the base IRI of this query
	 *
	 * @param iri the base IRI
	 * @return this
	 */
	public T base(Iri iri) {
		this.base = Optional.of(SparqlBuilder.base(iri));

		return (T) this;
	}

	/**
	 * Set the base IRI of this query
	 *
	 * @param iri the base IRI
	 * @return this
	 */
	public T base(IRI iri) {
		return base(iri(iri));
	}

	/**
	 * Set the Base clause of this query
	 *
	 * @param base the {@link Base} clause to set
	 * @return this
	 */
	public T base(Base base) {
		this.base = Optional.of(base);

		return (T) this;
	}

	/**
	 * Add prefix declarations to this query
	 *
	 * @param prefixes the prefixes to add
	 * @return this
	 */
	public T prefix(Prefix... prefixes) {
		this.prefixes = SparqlBuilderUtils.getOrCreateAndModifyOptional(this.prefixes, SparqlBuilder::prefixes,
				p -> p.addPrefix(prefixes));

		return (T) this;
	}

	/**
	 * Set the Prefix declarations of this query
	 *
	 * @param prefixes the {@link PrefixDeclarations} to set
	 * @return this
	 */
	public T prefix(PrefixDeclarations prefixes) {
		this.prefixes = Optional.of(prefixes);

		return (T) this;
	}

	protected abstract String getQueryActionString();

	@Override
	public String getQueryString() {
		StringBuilder query = new StringBuilder();

		SparqlBuilderUtils.appendAndNewlineIfPresent(base, query);
		SparqlBuilderUtils.appendAndNewlineIfPresent(prefixes, query);

		query.append(getQueryActionString());

		return query.toString();
	}

	protected void appendNamedTriplesTemplates(StringBuilder queryString, Optional<GraphName> graphName,
			TriplesTemplate triples) {
		queryString.append(graphName
				.map(graph -> SparqlBuilderUtils
						.getBracedString("GRAPH " + graph.getQueryString() + " " + triples.getQueryString()))
				.orElseGet(triples::getQueryString));
	}
}
