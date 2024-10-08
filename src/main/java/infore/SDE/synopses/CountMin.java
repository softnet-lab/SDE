package infore.SDE.synopses;



import com.fasterxml.jackson.databind.JsonNode;
import infore.SDE.messages.Estimation;
import infore.SDE.messages.Request;
import infore.SDE.synopses.Sketches.CM;
import java.io.*;

public class CountMin extends Synopsis implements Serializable {
	private static final long serialVersionUID = 1L;
	private CM cm;
	int count = 0;
	public CountMin(int uid, String[] parameters) {
		super(uid,parameters[0],parameters[1], parameters[2]);
		cm = new CM(Double.parseDouble(parameters[3]),Double.parseDouble(parameters[4]),Integer.parseInt(parameters[5]));
	}

	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

		try {
			// Serialize CM fields
			byte[] cmBytes = CM.serialize(cm);
			oos.writeObject(cmBytes);
		} catch (IOException e) {
			throw new IOException("Failed to serialize CM", e);
		}
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();

		try {
			// Read serialized CM bytes from input stream
			byte[] cmBytes = (byte[]) ois.readObject();
			// Deserialize CM
			cm = CM.deserialize(cmBytes);
		} catch (IOException | ClassNotFoundException e) {
			throw new IOException("Failed to deserialize CM", e);
		}
	}
	 
	@Override
	public void add(Object k) {
		//String j = (String)k;
		count++;
		//ObjectMapper mapper = new ObjectMapper();
		JsonNode node = (JsonNode)k;
        /*try {
            node = mapper.readTree(j);
        } catch (IOException e) {
            e.printStackTrace();
        } */

		String key = node.get(this.keyIndex).asText();

		if(this.valueIndex.startsWith("null")){

			cm.add(key, 1);
		}else{
			String value = node.get(this.valueIndex).asText();
			//cm.add(Math.abs((key).hashCode()), (long)Double.parseDouble(value));
			cm.add(key, (long)Double.parseDouble(value));
		}

	}

	@Override
	public Object estimate(Object k)
	{
		return Long.toString(cm.estimateCount(new Long((long) k)));
	}

	@Override
	public Synopsis merge(Synopsis sk) {
		return sk;	
	}

	@Override
	public Estimation estimate(Request rq) {


		if(rq.getRequestID() % 10 == 6){

			String[] par = rq.getParam();
			par[2]= ""+rq.getUID();
			rq.setUID(Integer.parseInt(par[1]));
			rq.setParam(par);
			rq.setNoOfP(rq.getNoOfP()*Integer.parseInt(par[0]));
			return new Estimation(rq, cm, par[1]);

		}
		String key = rq.getParam()[0];
		String e = Double.toString((double)cm.estimateCount(key));
		return new Estimation(rq, e, Integer.toString(rq.getUID()));


	}
	
	
	
	
}
