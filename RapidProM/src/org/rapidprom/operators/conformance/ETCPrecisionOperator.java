package org.rapidprom.operators.conformance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javassist.tools.rmi.ObjectNotFoundException;

import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.plugins.alignetc.AlignETCPlugin;
import org.processmining.plugins.alignetc.AlignETCSettings;
import org.processmining.plugins.alignetc.core.ReplayAutomaton;
import org.processmining.plugins.alignetc.result.AlignETCResult;
import org.processmining.plugins.petrinet.replayresult.PNMatchInstancesRepResult;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.replayer.replayresult.AllSyncReplayResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;
import org.rapidprom.external.connectors.prom.ProMPluginContextManager;
import org.rapidprom.ioobjects.PNRepResultIOObject;
import org.rapidprom.ioobjects.PetriNetIOObject;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.example.table.DataRow;
import com.rapidminer.example.table.DataRowFactory;
import com.rapidminer.example.table.MemoryExampleTable;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.Ontology;

public class ETCPrecisionOperator extends Operator {

	private static final String PARAMETER_1 = "Pruning (cut) threshold for the Automaton";
	
	private InputPort input1 = getInputPorts().createPort("model (PetriNet)", PetriNetIOObject.class);
	private InputPort input2 = getInputPorts().createPort("alignments (ProM PNRepResult)", PNRepResultIOObject.class);


	private OutputPort outputMetrics = getOutputPorts().createPort("example set (Data Table)");

	//private ExampleSetMetaData metaData = null;

	private final String NAMECOL = "Name";
	private final String VALUECOL = "Value";

	public ETCPrecisionOperator(OperatorDescription description) {
		super(description);

//		this.metaData = new ExampleSetMetaData();
//		AttributeMetaData amd1 = new AttributeMetaData(NAMECOL,
//				Ontology.STRING);
//		amd1.setRole(AttributeColumn.REGULAR);
//		amd1.setNumberOfMissingValues(new MDInteger(0));
//		metaData.addAttribute(amd1);
//		AttributeMetaData amd2 = new AttributeMetaData(VALUECOL,
//				Ontology.NUMERICAL);
//		amd2.setRole(AttributeColumn.REGULAR);
//		amd2.setNumberOfMissingValues(new MDInteger(0));
//		metaData.addAttribute(amd2);
//		metaData.setNumberOfExamples(2);
//		getTransformer()
//				.addRule(new GenerateNewMDRule(outputMetrics, this.metaData));
	}

	@Override
	public void doWork() throws OperatorException {
		Logger logger = LogService.getRoot();
		logger.log(Level.INFO,
				"Start: precision");
		long time = System.currentTimeMillis();

		PluginContext pluginContext = ProMPluginContextManager.instance().getContext();

		PetriNetIOObject net = input1.getData(PetriNetIOObject.class);
		PNRepResultIOObject alignment = input2.getData(PNRepResultIOObject.class);
		
		//Convert to n-alignments object
		Collection<AllSyncReplayResult> col = new ArrayList<AllSyncReplayResult>();
		for (SyncReplayResult rep : alignment.getArtifact()) {

			//Get all the attributes of the 1-alignment result
			List<List<Object>> nodes = new ArrayList<List<Object>>();
			nodes.add(rep.getNodeInstance());

			List<List<StepTypes>> types = new ArrayList<List<StepTypes>>();
			types.add(rep.getStepTypes());

			SortedSet<Integer> traces = rep.getTraceIndex();
			boolean rel = rep.isReliable();

			//Create a n-alignment result with this attributes
			AllSyncReplayResult allRep = new AllSyncReplayResult(nodes, types, -1, rel);
			allRep.setTraceIndex(traces);//The creator not allow add the set directly
			col.add(allRep);
		}
		PNMatchInstancesRepResult alignments = new PNMatchInstancesRepResult(col);

		AlignETCPlugin etc = new AlignETCPlugin();
		AlignETCResult res = new AlignETCResult();
		AlignETCSettings sett = new AlignETCSettings(res);

		ReplayAutomaton ra = null;
		try {
			ra = new ReplayAutomaton(pluginContext, alignments, net.getArtifact());
		} catch (ConnectionCannotBeObtained e1) {
			e1.printStackTrace();
		}
		ra.cut(0d); // TODO: turn the threshold parameter into a RapidProm parameter
		
		try {
			ra.extend(net.getArtifact(), net.getInitialMarking());
		} catch (IllegalTransitionException | ObjectNotFoundException e1) {
			e1.printStackTrace();
		}
		ra.conformance(res);
		
			
		ExampleSet es = null;
		MemoryExampleTable table = null;
		List<Attribute> attributes = new LinkedList<Attribute>();
		attributes.add(AttributeFactory.createAttribute(this.NAMECOL,
				Ontology.STRING));
		attributes.add(AttributeFactory.createAttribute(this.VALUECOL,
				Ontology.NUMERICAL));
		table = new MemoryExampleTable(attributes);
		fillTableWithRow(table, "Precision", res.ap, attributes);
		
		es = table.createExampleSet();
		outputMetrics.deliver(es);

		logger.log(Level.INFO, "End: precision ("+ (System.currentTimeMillis() - time) / 1000 + " sec)");

	}

	public List<ParameterType> getParameterTypes() {
		List<ParameterType> parameterTypes = super.getParameterTypes();

		ParameterTypeDouble parameterType1 = new ParameterTypeDouble(PARAMETER_1, PARAMETER_1, 0d, 1d, 0d);
		parameterTypes.add(parameterType1);

		return parameterTypes;
	}

	private void fillTableWithRow(MemoryExampleTable table, String name,
			Object value, List<Attribute> attributes) {
		// fill table
		DataRowFactory factory = new DataRowFactory(
				DataRowFactory.TYPE_DOUBLE_ARRAY, '.');
		Object[] vals = new Object[2];
		vals[0] = name;
		vals[1] = value;
		// convert the list to array
		Attribute[] attribArray = new Attribute[attributes.size()];
		for (int i = 0; i < attributes.size(); i++) {
			attribArray[i] = attributes.get(i);
		}
		DataRow dataRow = factory.create(vals, attribArray);
		table.addDataRow(dataRow);
	}
}
