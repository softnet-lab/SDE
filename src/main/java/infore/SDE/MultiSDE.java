package infore.SDE;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import infore.SDE.messages.Datapoint;
import infore.SDE.sources.kafkaProducerEstimation;
import infore.SDE.sources.KafkaStringConsumer;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.collector.selector.OutputSelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SplitStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import infore.SDE.transformations.ReduceFlatMap;
import infore.SDE.transformations.RqRouterFlatMap;
import infore.SDE.transformations.SDECoProcessFunction;
import infore.SDE.transformations.DataRouterCoFlatMap;
import infore.SDE.messages.Estimation;
import infore.SDE.messages.Request;

/**
 * <br>
 * Implementation code for SDE for INFORE-PROJECT" <br> *
 * ATHENA Research and Innovation Center <br> *
 * Author: Antonis_Kontaxakis <br> *
 * email: adokontax15@gmail.com *
 */

public class MultiSDE {

	private static String kafkaDataInputTopic;
	private static String kafkaRequestInputTopic;
	private static String kafkaBrokersList;
	private static int parallelism;
	private static int parallelism2;
	private static int multi;
	private static String kafkaOutputTopic;
	private static String kafkaUnionTopic;

	/**
	 * @param args Program arguments. You have to provide 4 arguments otherwise
	 *             DEFAULT values will be used.<br>
	 *             <ol>
	 *             <li>args[0]={@link #kafkaDataInputTopic} DEFAULT: "SpringI2")
	 *             <li>args[1]={@link #kafkaRequestInputTopic} DEFAULT: "rq13")
	 *             <li>args[2]={@link #kafkaBrokersList} (DEFAULT: "192.168.1.3:9092")
	 *             <li>args[3]={@link #parallelism} Job parallelism (DEFAULT: "6")
	 *             <li>
	 *             "O10")
	 *             </ol>
	 * @throws Exception
	 */

	public static void main(String[] args) throws Exception {

		// Initialize Input Parameters
		initializeParameters(args);
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setParallelism(parallelism);

		KafkaStringConsumer kc = new KafkaStringConsumer(kafkaBrokersList, kafkaDataInputTopic);
		KafkaStringConsumer requests = new KafkaStringConsumer(kafkaBrokersList, kafkaRequestInputTopic);
		kafkaProducerEstimation kp = new kafkaProducerEstimation(kafkaBrokersList, kafkaOutputTopic);
		KafkaStringConsumer union = new KafkaStringConsumer(kafkaBrokersList, kafkaUnionTopic);
		//kafkaProducerEstimation kp = new kafkaProducerEstimation(kafkaBrokersList, kafkaOutputTopic);
		//kafkaProducerEstimation test = new kafkaProducerEstimation(kafkaBrokersList, "testPairs");

		DataStream<String> datastream = env.addSource(kc.getFc());
		DataStream<String> RQ_stream = env.addSource(requests.getFc());
		DataStream<String> union_stream = env.addSource(union.getFc());
		//map kafka data input to tuple2<int,double>
		DataStream<Datapoint> dataStream = datastream
				.map(new MapFunction<String, Datapoint>() {

					@Override
					public Datapoint map(String node) throws IOException {
						// TODO Auto-generated method stub
						ObjectMapper objectMapper = new ObjectMapper();
						Datapoint dp = objectMapper.readValue(node, Datapoint.class);
						return dp;
					}
				}).keyBy((KeySelector<Datapoint, String>)Datapoint::getKey);

		//DataStream<Tuple2<String, String>> dataStream = datastream.flatMap(new IngestionMultiplierFlatMap(multi)).setParallelism(parallelism2).keyBy(0);

		DataStream<Request> RQ_Stream = RQ_stream
				.map(new MapFunction<String, Request>() {
					private static final long serialVersionUID = 1L;

					@Override
					public Request map(String node) throws IOException {
						// TODO Auto-generated method stub
						//String[] valueTokens = node.replace("\"", "").split(",");
						//if(valueTokens.length > 6) {
						ObjectMapper objectMapper = new ObjectMapper();

						// byte[] jsonData = json.toString().getBytes();
						Request request = objectMapper.readValue(node, Request.class);
						return  request;

					}
				}).keyBy((KeySelector<Request, String>) Request::getKey);

		DataStream<Estimation> UNION_stream = union_stream
				.map(new MapFunction<String, Estimation>() {
					private static final long serialVersionUID = 1L;

					@Override
					public Estimation map(String node) throws IOException {
						// TODO Auto-generated method stub
						//String[] valueTokens = node.replace("\"", "").split(",");
						//if(valueTokens.length > 6) {
						ObjectMapper objectMapper = new ObjectMapper();

						// byte[] jsonData = json.toString().getBytes();
						Estimation est = objectMapper.readValue(node, Estimation.class);
						return  est;

					}
				}).keyBy((KeySelector<Estimation, String>) Estimation::getKey);


		DataStream<Request> SynopsisRequests = RQ_Stream
				.flatMap(new RqRouterFlatMap()).keyBy((KeySelector<Request, String>) r -> r.getKey());

		DataStream<Datapoint> DataStream = dataStream.connect(RQ_Stream)
				.flatMap(new DataRouterCoFlatMap()).keyBy((KeySelector<Datapoint, String>) r -> r.getKey());

		DataStream<Estimation> estimationStream = DataStream.connect(SynopsisRequests)
				.process(new SDECoProcessFunction(false)).keyBy((KeySelector<Estimation, String>) r -> r.getKey());

		//estimationStream.addSink(kp.getProducer());
		//estimationStream.writeAsText("cm", FileSystem.WriteMode.OVERWRITE);

		SplitStream<Estimation> split = estimationStream.split(new OutputSelector<Estimation>() {
			private static final long serialVersionUID = 1L;
			@Override
			public Iterable<String> select(Estimation value) {
				// TODO Auto-generated method stub
				 List<String> output = new ArrayList<String>();
				 if (value.getNoOfP() == 1) {
			            output.add("single");
			        }
			        else {
			            output.add("multy");
			        }
			        return output;
				}
			});

		DataStream<Estimation> single = split.select("single");
		DataStream<Estimation> multy = split.select("multy");
		//UNION_stream.print();

		DataStream<Estimation> fmulty = UNION_stream.union(multy);

		//single.addSink(kp.getProducer());
		DataStream<Estimation> finalStream = fmulty.flatMap(new ReduceFlatMap());
		//DataStream<Tuple2< String, Object>> finalStream = estimationStream.flatMap(new ReduceFlatMap());
		//finalStream.addSink(kp.getProducer());

		JobExecutionResult result = env.execute("Streaming Multy SDE");
}

	private static void initializeParameters(String[] args) {

		if (args.length > 5) {

			System.out.println("[INFO] User Defined program arguments");
			//User defined program arguments
			kafkaDataInputTopic = args[0];
			kafkaRequestInputTopic = args[1];
			kafkaOutputTopic = args[2];
			kafkaBrokersList = args[3];
			//kafkaBrokersList = "localhost:9092";
			parallelism = Integer.parseInt(args[4]);
			parallelism2 = Integer.parseInt(args[5]);
			//multi = Integer.parseInt(args[5]);

		}else{

			System.out.println("[INFO] Default values");
			//Default values
			kafkaDataInputTopic = "FIN5001";
			kafkaRequestInputTopic = "testRequest16";
			kafkaUnionTopic = "testUnionTopic2";
			parallelism = 3;
			parallelism2 = 3;
			kafkaBrokersList = "192.168.1.104:9093,192.168.1.104:9094";
			kafkaOutputTopic = "4FINOUT";

		}
	}
}
