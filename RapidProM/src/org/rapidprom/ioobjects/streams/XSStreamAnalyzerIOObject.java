package org.rapidprom.ioobjects.streams;

import org.processmining.framework.plugin.PluginContext;
import org.processmining.stream.core.interfaces.XSDataPacket;
import org.processmining.streamanalysis.core.interfaces.XSStreamAnalyzer;
import org.rapidprom.ioobjects.abstr.AbstractRapidProMIOObject;

public class XSStreamAnalyzerIOObject<D extends XSDataPacket<?, ?>, R, REF>
		extends AbstractRapidProMIOObject<XSStreamAnalyzer<D, R, REF>> {

	private static final long serialVersionUID = 7205510572450073788L;

	public XSStreamAnalyzerIOObject(XSStreamAnalyzer<D, R, REF> t,
			PluginContext context) {
		super(t, context);
	}

}
