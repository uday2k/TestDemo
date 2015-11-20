/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.jdbc;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.util.AbstractExpressionEvaluator;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * An implementation of {@link SqlParameterSourceFactory} which creates an {@link SqlParameterSource} that evaluates
 * Spring EL expressions. In addition the user can supply static parameters that always take precedence.
 *
 * @author Dave Syer
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.0
 */
public class ExpressionEvaluatingSqlParameterSourceFactory extends AbstractExpressionEvaluator implements
		SqlParameterSourceFactory {

	private final static Log logger = LogFactory.getLog(ExpressionEvaluatingSqlParameterSourceFactory.class);

	private static final ExpressionParser PARSER = new SpelExpressionParser();

	private static final Object ERROR = new Object();

	private volatile Map<String, ?> staticParameters;

	/**
	 * The {@link Map} of parameters with expressions.
	 * {@code key} - parameter name; {@code value} - array of two {@link Expression}s:
	 * first element - direct {@link Expression},  second - collection projection {@link Expression}.
	 * Used in case of root object of evaluation is {@link Collection}.
	 */
	private volatile Map<String, Expression[]> parameterExpressions;

	public ExpressionEvaluatingSqlParameterSourceFactory() {
		this.staticParameters = Collections.unmodifiableMap(new HashMap<String, Object>());
		this.parameterExpressions = new HashMap<String, Expression[]>();
	}

	/**
	 * Define some static parameter values. These take precedence over those defined as expressions in the
	 * {@link #setParameterExpressions(Map) parameterExpressions}, so a parameter in the query will be filled from here
	 * first, and then from the expressions.
	 *
	 * @param staticParameters the static parameters to set
	 */
	public void setStaticParameters(Map<String, ?> staticParameters) {
		this.staticParameters = staticParameters;
	}

	/**
	 * Optionally maps parameter names to explicit expressions. The named parameter support in Spring is limited to
	 * simple parameter names with no special characters, so this feature allows you to specify a simple name in the SQL
	 * query and then have it translated into an expression at runtime. The target of the expression depends on the
	 * context: generally in an outbound setting it is a Message, and in an inbound setting it is a result set row (a
	 * Map or a domain object if a RowMapper has been provided). The {@link #setStaticParameters(Map) static parameters}
	 * can be referred to in an expression using the variable <code>#staticParameters</code>, for example:
	 * <p>&nbsp;
	 * <table>
	 * <caption>Parameter Expressions Samples</caption>
	 * <tr>
	 * <th><b>Key</b></th>
	 * <th><b>Value (Expression)</b></th>
	 * <th><b>Example SQL</b></th>
	 * </tr>
	 * <tr>
	 * <td>id</td>
	 * <td>{@code payload.businessKey}</td>
	 * <td>{@code select * from items where id=:id}</td>
	 * </tr>
	 * <tr>
	 * <td>date</td>
	 * <td>{@code headers['timestamp']}</td>
	 * <td>{@code select * from items where created>:date}</td>
	 * </tr>
	 * <tr>
	 * <td>key</td>
	 * <td>{@code #staticParameters['foo'].toUpperCase()}</td>
	 * <td>{@code select * from items where name=:key}</td>
	 * </tr>
	 * </table>
	 * <p>
	 *
	 * @param parameterExpressions the parameter expressions to set
	 */
	public void setParameterExpressions(Map<String, String> parameterExpressions) {
		Map<String, Expression[]> paramExpressions = new HashMap<String, Expression[]>(parameterExpressions.size());
		for (Map.Entry<String, String> entry : parameterExpressions.entrySet()) {
			String key = entry.getKey();
			String expression = entry.getValue();
			Expression[] expressions = new Expression[] {
					PARSER.parseExpression(expression),
					PARSER.parseExpression("#root.![" + expression + "]")
			};
			paramExpressions.put(key, expressions);
		}
		this.parameterExpressions = paramExpressions;
	}

	@Override
	public SqlParameterSource createParameterSource(final Object input) {
		return new ExpressionEvaluatingSqlParameterSource(input, this.staticParameters, this.parameterExpressions, true);
	}

	/**
	 * Create an expression evaluating {@link SqlParameterSource} that does not cache it's results. Useful for cases
	 * where the source is used multiple times, for example in a {@code <int-jdbc:inbound-channel-adapter/>} for the
	 * {@code select-sql-parameter-source} attribute.
	 * @param input The root object for the evaluation.
	 * @return The parameter source.
	 */
	public SqlParameterSource createParameterSourceNoCache(final Object input) {
		return new ExpressionEvaluatingSqlParameterSource(input, this.staticParameters, this.parameterExpressions, false);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		this.getEvaluationContext().setVariable("staticParameters", this.staticParameters);
	}

	private final class ExpressionEvaluatingSqlParameterSource extends AbstractSqlParameterSource {

		private final Object input;

		private final Map<String, Object> values = new HashMap<String, Object>();

		private final Map<String, Expression[]> parameterExpressions;

		private final boolean cache;

		private ExpressionEvaluatingSqlParameterSource(Object input, Map<String, ?> staticParameters,
				Map<String, Expression[]> parameterExpressions, boolean cache) {
			this.input = input;
			this.parameterExpressions = parameterExpressions;
			this.values.putAll(staticParameters);
			this.cache = cache;
		}

		@Override
		public Object getValue(String paramName) throws IllegalArgumentException {
			return this.doGetValue(paramName, false);
		}

		public Object doGetValue(String paramName, boolean calledFromHasValue) throws IllegalArgumentException {
			if (values.containsKey(paramName)) {
				Object cachedByHasValue = values.get(paramName);
				if (!this.cache) {
					values.remove(paramName);
				}
				return cachedByHasValue;
			}

			if (!parameterExpressions.containsKey(paramName)) {
				Expression[] expressions = new Expression[] {
						PARSER.parseExpression(paramName),
						PARSER.parseExpression("#root.![" + paramName + "]")
				};
				ExpressionEvaluatingSqlParameterSourceFactory.this.parameterExpressions.put(paramName, expressions);
				this.parameterExpressions.put(paramName, expressions);
			}

			Expression expression = null;

			if (input instanceof Collection<?>) {
				expression = parameterExpressions.get(paramName)[1];
			}
			else {
				expression = parameterExpressions.get(paramName)[0];
			}

			Object value = evaluateExpression(expression, input);
			if (this.cache || calledFromHasValue) {
				values.put(paramName, value);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Resolved expression " + expression + " to " + value);
			}
			return value;
		}

		@Override
		public boolean hasValue(String paramName) {
			try {
				Object value = doGetValue(paramName, true);
				if (value == ERROR) {
					return false;
				}
			}
			catch (ExpressionException e) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not evaluate expression", e);
				}
				if (this.cache) {
					values.put(paramName, ERROR);
				}
				return false;
			}
			return true;
		}
	}

}
