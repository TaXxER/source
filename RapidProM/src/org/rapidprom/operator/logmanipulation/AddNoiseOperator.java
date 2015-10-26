package org.rapidprom.operator.logmanipulation;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.rapidprom.ioobjects.XLogIOObject;

import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.GenerateNewMDRule;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeCategory;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.UndefinedParameterError;
import com.rapidminer.tools.LogService;

public class AddNoiseOperator extends Operator {

	private static final String PARAMETER_1 = "Noise Percentage",
			PARAMETER_2 = "Noise Type", PARAMETER_3 = "Seed";
	private static final String HEAD = "Remove Head", BODY = "Remove Body",
			EXTRA = "Add Event", SWAP = "Swap Tasks", REMOVE = "Remove Task";

	private InputPort inputXLog = getInputPorts().createPort(
			"event log (ProM Event Log)", XLogIOObject.class);
	private OutputPort outputEventLog = getOutputPorts().createPort(
			"event log (ProM Event Log)");

	public AddNoiseOperator(OperatorDescription description) {
		super(description);
		getTransformer().addRule(
				new GenerateNewMDRule(outputEventLog, XLogIOObject.class));
	}

	public void doWork() throws OperatorException {
		Logger logger = LogService.getRoot();
		logger.log(Level.INFO,
				"Start: add artificial start and end event to all traces");
		long time = System.currentTimeMillis();

		XLogIOObject xLogIOObject = inputXLog.getData(XLogIOObject.class);
		XLog logOriginal = xLogIOObject.getArtifact();
		XLog logModified = filterLog(logOriginal);
		XLogIOObject result = new XLogIOObject(logModified,
				xLogIOObject.getPluginContext());
		outputEventLog.deliver(result);
		logger.log(Level.INFO,
				"End: add artificial start and end event to all traces ("
						+ (System.currentTimeMillis() - time) / 1000 + " sec)");
	}

	private XLog filterLog(XLog log) throws UndefinedParameterError {
		XLog result = XFactoryRegistry.instance().currentDefault()
				.createLog(log.getAttributes());
		XFactoryRegistry.instance().setCurrentDefault(new XFactoryNaiveImpl());
		int traceCounter = 0;
		Random rOverall = new Random(getParameterAsInt(PARAMETER_3));
		for (XTrace t : log) {
			XTrace copy = XFactoryRegistry.instance().currentDefault()
					.createTrace(t.getAttributes());
			Random r = new Random(getParameterAsInt(PARAMETER_3)
					+ new Integer(traceCounter).hashCode());
			double nextDouble = rOverall.nextDouble();
			// System.out.println("nextDouble:" + nextDouble);
			if (nextDouble < getParameterAsDouble(PARAMETER_1)) {
				double oneThird = t.size() / 3.0;
				if (getParameterAsString(PARAMETER_2).equals(HEAD)) {
					int start = safeNextInt(r, (int) oneThird);
					for (int i = start; i < t.size(); i++) {
						XEvent e = t.get(i);
						XEvent copyEvent = XFactoryRegistry.instance()
								.currentDefault()
								.createEvent(e.getAttributes());
						copy.add(copyEvent);
					}
				} else if (getParameterAsString(PARAMETER_2).equals(BODY)) {
					int stopFirst = safeNextInt(r, (int) oneThird);
					for (int i = 0; i < stopFirst; i++) {
						XEvent e = t.get(i);
						XEvent copyEvent = XFactoryRegistry.instance()
								.currentDefault()
								.createEvent(e.getAttributes());
						copy.add(copyEvent);
					}

					int startLast = t.size() - safeNextInt(r, (int) oneThird);
					for (int i = startLast; i < t.size(); i++) {
						XEvent e = t.get(i);
						XEvent copyEvent = XFactoryRegistry.instance()
								.currentDefault()
								.createEvent(e.getAttributes());
						copy.add(copyEvent);
					}
				} else if (getParameterAsString(PARAMETER_2).equals(EXTRA)) {
					for (XEvent e : t) {
						XEvent copyEvent = XFactoryRegistry.instance()
								.currentDefault()
								.createEvent(e.getAttributes());
						copy.add(copyEvent);
					}
					// add event
					int pos = safeNextInt(r, t.size());

					System.out.println("Pos: " + pos);

					// get the previous event to check for timestamp
					Date lowb = (pos != 0) ? XTimeExtension.instance()
							.extractTimestamp(copy.get(pos - 1)) : null;
					Date upb = (pos != t.size()) ? XTimeExtension.instance()
							.extractTimestamp(copy.get(pos)) : null;

					// if(lowb!= null)
					// System.out.println("Low: " + lowb.toString());
					//
					// if(upb!=null)
					// System.out.println("Up: " + upb.toString());

					if ((lowb != null) && (upb != null)) {
						// the new event has timestamp in between
						copy.add(
								pos,
								createEvent(
										log,
										log.size(),
										r,
										new Date((upb.getTime() + lowb
												.getTime()) / 2),
										XTimeExtension.instance()));
					} else if (lowb != null) {
						// there is a lower bound
						copy.add(
								pos,
								createEvent(log, log.size(), r,
										new Date(lowb.getTime() + 1),
										XTimeExtension.instance()));
					} else if (upb != null) {
						// there is an upper bound
						copy.add(
								pos,
								createEvent(log, log.size(), r,
										new Date(upb.getTime() - 1),
										XTimeExtension.instance()));
					} else {
						// there is neither a lower or an upper bound
						copy.add(
								pos,
								createEvent(log, log.size(), r, null,
										XTimeExtension.instance()));
					}
				} else if (getParameterAsString(PARAMETER_2).equals(SWAP)) {
					int indexFirstTaskToSwap = safeNextInt(r, t.size());
					int indexSecondTaskToSwap = safeNextInt(r, t.size());
					XEvent firstTaskToSwap = null;
					XEvent secondTaskToSwap = null;
					XEvent event = null;
					if (indexFirstTaskToSwap != indexSecondTaskToSwap) {
						// it makes sense to swap
						firstTaskToSwap = t.get(indexSecondTaskToSwap);
						secondTaskToSwap = t.get(indexFirstTaskToSwap);
						// swap also the timestamps
						Date firstTimestamp = XTimeExtension.instance()
								.extractTimestamp(firstTaskToSwap);
						Date secondTimestamp = XTimeExtension.instance()
								.extractTimestamp(secondTaskToSwap);

						for (int i = 0; i < t.size(); i++) {
							if (i == indexFirstTaskToSwap) {
								event = (XEvent) firstTaskToSwap.clone();
								XTimeExtension.instance().assignTimestamp(
										event, secondTimestamp);
							} else if (i == indexSecondTaskToSwap) {
								event = (XEvent) secondTaskToSwap.clone();
								XTimeExtension.instance().assignTimestamp(
										event, firstTimestamp);
							} else {
								event = t.get(i);
							}
							XEvent copyEvent = XFactoryRegistry
									.instance()
									.currentDefault()
									.createEvent(
											(XAttributeMap) event
													.getAttributes().clone());
							copy.add(copyEvent);

						}
					} else {
						// we still need to copy
						for (XEvent e : t) {
							XEvent copyEvent = XFactoryRegistry
									.instance()
									.currentDefault()
									.createEvent(
											(XAttributeMap) e.getAttributes()
													.clone());
							copy.add(copyEvent);
						}
					}
				} else {
					// remove an event
					int pos = Math.abs(r.nextInt()) % (t.size() + 1);
					for (int i = 0; i < t.size(); i++) {
						if (i != pos) {
							XEvent event = t.get(i);
							XEvent copyEvent = XFactoryRegistry.instance()
									.currentDefault()
									.createEvent(event.getAttributes());
							copy.add(copyEvent);
						}
					}
				}
			} else {
				for (XEvent e : t) {
					XEvent copyEvent = XFactoryRegistry.instance()
							.currentDefault().createEvent(e.getAttributes());
					copy.add(copyEvent);
				}
			}
			traceCounter++;
			result.add(copy);
		}
		return result;
	}

	private int safeNextInt(Random r, int maxInt) {
		return r.nextInt(maxInt > 0 ? maxInt : 1);
	}

	protected XEvent createEvent(XLog log, int logSize, Random rand, Date date,
			XTimeExtension xTime) {
		// both date are null
		XTrace tr = log.get(Math.abs(rand.nextInt()) % logSize);
		int pos = safeNextInt(rand, tr.size());

		if (pos == 0 && pos < tr.size() - 1) // so it does not create "start"
												// events
			pos++;

		XEvent newEvt = XFactoryRegistry
				.instance()
				.currentDefault()
				.createEvent(
						(XAttributeMap) tr.get(pos).getAttributes().clone());
		if (date != null) {
			xTime.assignTimestamp(newEvt, date);
		}
		return newEvt;
	}

	public List<ParameterType> getParameterTypes() {
		List<ParameterType> parameterTypes = super.getParameterTypes();

		ParameterTypeDouble parameterType1 = new ParameterTypeDouble(
				PARAMETER_1, PARAMETER_1, 0, 1, 0.05);
		parameterTypes.add(parameterType1);

		String[] par2categories = new String[] { REMOVE, HEAD, BODY, EXTRA,
				SWAP };
		ParameterTypeCategory parameterType2 = new ParameterTypeCategory(
				PARAMETER_2, PARAMETER_2, par2categories, 0);
		parameterTypes.add(parameterType2);

		ParameterTypeInt parameterType3 = new ParameterTypeInt(PARAMETER_3,
				PARAMETER_3, 0, Integer.MAX_VALUE, 1);
		parameterTypes.add(parameterType3);
		return parameterTypes;
	}

}
