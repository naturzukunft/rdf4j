/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.sail.base.config;

import static org.eclipse.rdf4j.model.util.Values.iri;

import org.eclipse.rdf4j.model.IRI;

/**
 * Defines constants for the BaseSail schema.
 */
public class BaseSailSchema {

	/** The BaseSail schema namespace (<var>http://www.openrdf.org/config/sail/base#</var>). */
	public static final String NAMESPACE = "http://www.openrdf.org/config/sail/base#";

	/** <var>http://www.openrdf.org/config/sail/base#evaluationStrategyFactory</var> */
	public final static IRI EVALUATION_STRATEGY_FACTORY = iri(NAMESPACE, "evaluationStrategyFactory");

	public final static IRI DEFAULT_QUERY_EVALUATION_MODE = iri(NAMESPACE, "defaultQueryEvaluationMode");

}
