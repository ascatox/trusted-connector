/*-
 * ========================LICENSE_START=================================
 * LUCON Data Flow Policy Engine
 * %%
 * Copyright (C) 2017 Fraunhofer AISEC
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
 * =========================LICENSE_END==================================
 */
package de.fhg.ids.dataflowcontrol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alice.tuprolog.InvalidTheoryException;
import alice.tuprolog.MalformedGoalException;
import alice.tuprolog.NoMoreSolutionException;
import alice.tuprolog.NoSolutionException;
import alice.tuprolog.PrologException;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;
import alice.tuprolog.Var;
import de.fhg.aisec.ids.api.policy.DecisionRequest;
import de.fhg.aisec.ids.api.policy.Obligation;
import de.fhg.aisec.ids.api.policy.PAP;
import de.fhg.aisec.ids.api.policy.PDP;
import de.fhg.aisec.ids.api.policy.PolicyDecision;
import de.fhg.aisec.ids.api.policy.PolicyDecision.Decision;
import de.fhg.aisec.ids.api.policy.ServiceNode;
import de.fhg.aisec.ids.api.policy.TransformationDecision;
import de.fhg.aisec.ids.api.router.RouteManager;
import de.fhg.aisec.ids.api.router.RouteVerificationProof;
import de.fhg.ids.dataflowcontrol.lucon.LuconEngine;

/**
 * servicefactory=false is the default and actually not required. But we want to make
 * clear that this is a singleton, i.e. there will only be one instance of
 * PolicyDecisionPoint within the whole runtime.
 * 
 * @author Julian Schuette (julian.schuette@aisec.fraunhofer.de)
 *
 */
@Component(enabled = true, immediate = true, name = "ids-dataflow-control", servicefactory = false)
public class PolicyDecisionPoint implements PDP, PAP {
	private static final Logger LOG = LoggerFactory.getLogger(PolicyDecisionPoint.class);
	private static final String LUCON_FILE_EXTENSION = ".pl";	
	private LuconEngine engine;
	private RouteManager routeManager;
	
	/**
	 * Creates a query to retrieve policy decision from Prolog knowledge base.
	 * 
	 * Result of query will be:
	 *
	 * Target			Decision	Alternative		Obligation
	 * 	hadoopClusters	D			drop			(delete_after_days(_1354),_1354<30)	1
	 *	hiveMqttBroker	drop		Alt				A
	 * @param msgLabels 
	 */
	private String createDecisionQuery(ServiceNode target, Map<String, Object> msgLabels) {			
		StringBuilder sb = new StringBuilder();
		sb.append("rule(_X), has_target(_X, T), ");
		sb.append("has_endpoint(T, EP), ");
		sb.append("regex(EP, \"").append(target.getEndpoint()).append("\", _D), _D, ");
		msgLabels.keySet()
			.stream()
			.filter( k -> k.startsWith(LABEL_PREFIX) && msgLabels.get(k)!=null && !msgLabels.get(k).toString().equals(""))
			.forEach(k -> {
					sb.append("receives_label(T, ").append(msgLabels.get(k).toString()).append("), ");
			});
		if (target.getCapabilties().size() + target.getProperties().size() > 0) {
			sb.append("(");
		}
		for (String cap: target.getCapabilties()) {
			sb.append("(has_capability(T, \"").append(cap).append("\"); ");
		}
		for (String prop: target.getProperties()) {
			sb.append("has_property(T, \"").append(prop).append("\"), ");
		}
		if (target.getCapabilties().size() + target.getProperties().size() > 0) {
			sb.append("), ");
		}
		sb.append("(has_decision(_X, D); (has_obligation(_X, _O), has_alternativedecision(_O, Alt), requires_prerequisite(_O, A))).");
		return sb.toString();
	}
	
	/**
	 * A transformation query retrieves the set of labels to add and to remove from the Prolog knowledge base.
	 * 
	 * This method returns the respective query for a specific target.
	 * 
	 * @param target
	 * @return
	 */
	private String createTransformationQuery(ServiceNode target) {			
		StringBuilder sb = new StringBuilder();
		sb.append("service(_T), ");
		if (target.getEndpoint()!=null) {
			sb.append("has_endpoint(_T, _EP), ");
			sb.append("regex(_EP, \"").append(target.getEndpoint()).append("\", _X), _X, ");
		}
		if (target.getCapabilties().size() + target.getProperties().size() > 0) {
			sb.append("(");
		}
		for (String cap: target.getCapabilties()) {
			sb.append("(has_capability(_T, \"").append(cap).append("\"); ");
		}
		for (String prop: target.getProperties()) {
			sb.append("has_property(_T, \"").append(prop).append("\"), ");
		}
		if (target.getCapabilties().size() + target.getProperties().size() > 0) {
			sb.append("), ");
		}
		sb.append("creates_label(_T, Creates), removes_label(_T, Removes).");
		return sb.toString();
	}

	@Activate
	public void activate(ComponentContext ctx) throws IOException {
		if (this.engine == null) {
			this.engine = new LuconEngine(System.out);
		}
		
		// Try to load existing policies from deploy dir at activation
		File dir = new File(System.getProperty("karaf.base") + File.separator + "deploy");
		File[] directoryListing = dir.listFiles();
		if (directoryListing==null || !dir.isDirectory()) {
			LOG.warn("Unexpected: Not a directory: " + dir.getAbsolutePath());
			return;
		}
		
		boolean loaded = false;
		for (File f : directoryListing) {
	      if (f.getName().endsWith(LUCON_FILE_EXTENSION)) {
	    	  if (!loaded) {
	    		  LOG.info("Loading Lucon policy from " + f.getAbsolutePath());
	    		  loadPolicy(new FileInputStream(f));
		    	  loaded = true;
	    	  } else {
	    		  LOG.warn("Multiple policy files. Will load only one! " + f.getAbsolutePath());
	    	  }
	      }
	    }
	}
	
	@Reference(name="pdp-routemanager", policy=ReferencePolicy.DYNAMIC, cardinality=ReferenceCardinality.OPTIONAL)
	public void bindRouteManager(RouteManager routeManager) {
		LOG.warn("RouteManager bound. Camel routes can be analyzed");
		this.routeManager = routeManager;
	}
	public void unbindRouteManager(RouteManager routeManager) {
		LOG.warn("RouteManager unbound. Will not be able to verify Camel routes against policies anymore");
		this.routeManager = null;
	}

	@Override
	public TransformationDecision requestTranformations(ServiceNode lastServiceNode) {
		TransformationDecision result = new TransformationDecision();
		
		// Query prolog for labels to remove or add from message
		String query = this.createTransformationQuery(lastServiceNode);
		LOG.info("QUERY: " + query);
		try {
			List<SolveInfo> solveInfo = this.engine.query(query, false);
			if (solveInfo.isEmpty()) {
				return result;
			}
			
			// Get solutions, convert label variables to string and collect in sets
			
			// Collect labels to remove			
			List<Var> vars = solveInfo.get(0).getBindingVars();
			Set<String> labelsToRemove = new HashSet<>();
			for (Var var : vars) {
				if ("Removes".equals(var.getName()) && var.getLink() instanceof Struct) {
					Struct labelStruct = ((Struct) var.getLink());
					int labelCount = labelStruct.getArity();
					for (int i=0;i<labelCount;i++) {
						Term label = labelStruct.getTerm(i);
						if (!label.isEmptyList()) {
							labelsToRemove.add(label.toString());
						}
					}
				}
			}

			// Collect labels to add
			Set<String> labelsToAdd = new HashSet<>();
			for (Var var : vars) {
				if ("Creates".equals(var.getName()) && var.getLink() instanceof Struct) {
					Struct labelStruct = ((Struct) var.getLink());
					int labelCount = labelStruct.getArity();
					for (int i=0;i<labelCount;i++) {
						Term label = labelStruct.getTerm(i);
						if (!label.isEmptyList()) {
							labelsToAdd.add(label.toString());
						}
					}
				}
			}
			
			result.getLabelsToRemove().addAll(labelsToRemove);
			result.getLabelsToAdd().addAll(labelsToAdd);		
		} catch (NoMoreSolutionException | MalformedGoalException | NoSolutionException e) {
			LOG.error(e.getMessage(), e);
		}
		return result;
	}
	
	@Override
	public PolicyDecision requestDecision(DecisionRequest req) {
		LOG.debug("Decision requested " + req.getFrom() + " -> " + req.getTo());
		PolicyDecision dec = new PolicyDecision();
		dec.setDecision(Decision.ALLOW); // Default value
		dec.setReason("Not yet ready for productive use!");

		try {
			// Query Prolog engine for a policy decision
			long startTime = System.nanoTime();
			String query = this.createDecisionQuery(req.getTo(), req.getMessageCtx());
			LOG.info("QUERY: " + query);
			List<SolveInfo> solveInfo = this.engine.query(query, false);
			long time = System.nanoTime() - startTime;
			LOG.info("Policy decision took " + time + " nanos");
						
			// Just for debugging
			if (LOG.isDebugEnabled()) {
				debug(solveInfo);
			}
			
			// If there is no matching rule, allow by default
			if (solveInfo.isEmpty()) {
				return dec;
			}
			
			// Get some obligation, if any 
			// TODO This is still incorrect because it only finds "any" obligation. Merge obligations of all matching rules.
			List<Var> vars = solveInfo.get(0).getBindingVars();
			Optional<Var> alt = vars.stream().filter(v -> "Alt".equals(v.getName())).findAny();
			Optional<Var> action = vars.stream().filter(v -> "A".equals(v.getName())).findAny();
			if (action.isPresent()) {
				Obligation o = new Obligation();				
				o.setAction(action.get().getTerm().toString());
				if (alt.isPresent() && "drop".equals(alt.get().getTerm().toString())) {
					o.setAlternativeDecision(Decision.DENY);
				}
				dec.setObligation(o);
				dec.setDecision(Decision.ALLOW);
			}
			
			Optional<Var> decision = vars.stream().filter(v -> "D".equals(v.getName()) && v.isBound()).findAny();
			if (decision.isPresent() && "drop".equals(decision.get().getTerm().toString())) {
					dec.setDecision(Decision.DENY);				
			}
		} catch (NoMoreSolutionException | MalformedGoalException | NoSolutionException e) {
			LOG.error(e.getMessage(), e);
			dec.setDecision(Decision.DENY);
		}
		return dec;
	}

	/**
	 * Just for debugging: Print query solution to DEBUG out.
	 * 
	 * @param solveInfo
	 * @throws NoSolutionException
	 */
	private void debug(List<SolveInfo> solveInfo) throws NoSolutionException {
		for (SolveInfo i: solveInfo) {
			if (i.isSuccess()) {
				List<Var> vars = i.getBindingVars();
				vars.forEach(v -> LOG.debug(v.getName() + ":" + v.getTerm() + " bound: " + v.isBound()));
			}
		}
	}

	@Override
	public void clearAllCaches() {
		// Nothing to do here at the moment
	}

	@Override
	public void loadPolicy(InputStream is) {
		try {
			// Load policy into engine, possibly overwriting the existing one.
			this.engine.loadPolicy(is);
		} catch (InvalidTheoryException e) {
			LOG.error("Error in " + e.line + " " + e.pos + ": " + e.clause + ": " + e.getMessage(), e);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	@Override
	public List<String> listRules() {
		ArrayList<String> result = new ArrayList<>();
		try {
			List<SolveInfo> rules = this.engine.query("rule(X).", true);
			for (SolveInfo r : rules) {
				result.add(r.getVarValue("X").toString());
			}
		} catch (PrologException e) {
			LOG.error("Prolog error while retrieving rules " + e.getMessage(), e);
		}
		return result;
	}

	@Override
	public String getPolicy() {
		return this.engine.getTheory();
	}
	
	@Override
	public RouteVerificationProof verifyRoute(String routeId) {
		RouteManager rm = this.routeManager;
		if (rm == null) {
			LOG.warn("No RouteManager. Cannot verify Camel route " + routeId);
			return null;
		}
		
		String routePl = rm.getRouteAsProlog(routeId);
		if (routePl == null) {
			LOG.warn("Could not obtain Prolog representation of route " + routeId);
			return null;
		}
		
		return engine.proofInvalidRoute(routeId, routePl);
	}
}