package de.codecentric.robot.mongodblibrary.keywords;

import static com.mongodb.util.JSON.parse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.process.distribution.GenericVersion;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * 
 * Tests for {@link MongodbLibrary}
 * 
 * @author max.hartmann
 *
 */
public class MongodbLibraryTest {

	private static final Integer MONGO_TEST_PORT = 27020;
	
	private MongodbLibrary library;
	private MongoClient mongoClient;
	private DB db1;
	
	private static MongodExecutable mongodExecutable;
	
	@BeforeClass
	public static void startMongoDB() throws IOException {
		MongodConfig mongodConfig = new MongodConfig(new GenericVersion("2.4.5"), MONGO_TEST_PORT, Network.localhostIsIPv6());
		MongodStarter runtime = MongodStarter.getDefaultInstance();
		mongodExecutable = runtime.prepare(mongodConfig);
		mongodExecutable.start();
	}
	
	@AfterClass
	public static void shutdownMongoDB() {
		mongodExecutable.stop();
	}
	
	@Before
	public void setUp() throws UnknownHostException {
		library = new MongodbLibrary();
		library.connectToServer("localhost", MONGO_TEST_PORT.toString(), "robotdb1");
		mongoClient = new MongoClient("localhost" , MONGO_TEST_PORT );
		db1 = mongoClient.getDB("robotdb1");
		mongoClient.getDB("robotdb2");
	}
	
	@After
	public void tearDown() {
		db1.getCollection("testCol").drop();
		mongoClient.dropDatabase("robotdb1");
		mongoClient.dropDatabase("robotdb2");
	}
	
	@Test
	public void shouldReturnVersion() {
		//when
		String version = MongodbLibrary.VERSION;
		//then
		assertThat(version, is(notNullValue()));
	}
	
	@Test
	public void shouldInsertDocumentIntoCollection() {
		//given
		DBCollection collection = db1.getCollection("testCol");
		String json = "{say : 'Hello MongoDb!'}";
		//when
		library.insertDocument("testCol", json);
		//then
		DBObject object = collection.find().next();
		assertThat(object, is(notNullValue()));
		assertThat(object.containsField("say"), is(Boolean.TRUE));
		assertThat((String)object.get("say"), is("Hello MongoDb!"));
	}

	@Test
	public void shouldUpdateDocuments() {
		//given
		db1.getCollection("testCol1").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol1").insert((DBObject) JSON.parse("{name : 'Peter', age: 40}"));
		//when
		library.updateDocuments("testCol1", "{ age: 22 }", "{ $inc: { age: 1 } }");
		//then
		assertThat((Integer)db1.getCollection("testCol1").findOne((DBObject)parse("{ name : 'Max'}")).get("age"), is(23));
		assertThat((Integer)db1.getCollection("testCol1").findOne((DBObject)parse("{ name : 'Peter'}")).get("age"), is(40));
	}
	
	@Test
	public void shouldImportDocumentsFromArray() throws IOException {
		//given
		String path = "src/test/data/testArray.json";
		String collectionName = "testCol";
		//when
		library.importDocuments(collectionName, path);
		//then
		assertThat(db1.getCollection("testCol").count(), is(2l));
	}

	@Test
	public void shouldImportDocumentsFromSingleObject() throws IOException {
		//given
		String path = "src/test/data/testSingleObject.json";
		String collectionName = "testCol";
		//when
		library.importDocuments(collectionName, path);
		//then
		assertThat(db1.getCollection("testCol").count(), is(1l));
	}

	@Test
	public void shouldImportDocumentsRowSeperated() throws IOException {
		//given
		String path = "src/test/data/testRowSeperated.json";
		String collectionName = "testCol";
		//when
		library.importDocumentsRowSeperated(collectionName, path);
		//then
		assertThat(db1.getCollection("testCol").count(), is(16l));
	}
	
	@Test
	public void shouldDropCollection() {
		//given
		DBCollection collection = db1.getCollection("testCol");
		DBObject object = new BasicDBObject("say", "HelloMongoDB");
		collection.insert(object);
		//when
		library.dropCollection("testCol");
		//then
		assertThat(db1.getCollection("testCol").find().hasNext(), is(false));
	}
	
	@Test
	public void shouldUseDatabase() {
		//given
		String databaseName = "robotdb2";
		//when
		library.useDatabase(databaseName);
		//then
		assertThat(library.getDb().getName(), is(databaseName));
	}
	
	@Test
	public void shouldDropDatabase() {
		//given
		String databaseName = "robotdb2";
		//when
		library.dropDatabase("robotdb2");
		//then
		assertThat(mongoClient.getDatabaseNames().contains(databaseName), is(false));
	}
	
	@Test
	public void shouldCreateCollection() {
		//given
		String collectionName = "newCollection";
		//when
		library.createCollection(collectionName);
		//then
		assertThat(db1.getCollectionNames().contains(collectionName), is(true));
	}
	
	@Test
	public void shouldCreateCollectionWithParameters() {
		//given
		String collectionName = "newCollection";
		//when
		library.createCollectionWithOptions(collectionName, "{size : 1000}");
		//then
		assertThat(db1.getCollectionNames().contains(collectionName), is(true));
	}
	
	@Test
	public void shouldEnsureIndex() {
		//given
		String collectionName = "testCol";
		//when
		library.ensureIndex(collectionName, "{a : 1, b : -1}");
		//then
		List<DBObject> indexInfo = db1.getCollection(collectionName).getIndexInfo();
		DBObject key = (DBObject) indexInfo.get(1).get("key");
		assertThat(indexInfo.size(), is(2));
		assertThat(key.get("a").toString(), is("1"));
		assertThat(key.get("b").toString(), is("-1"));
	}

	@Test
	public void shouldEnsureUniqueIndexWithGivenName() {
		//given
		String collectionName = "testCol";
		String indexName = "myIndex";
		//when
		library.ensureUniqueIndex(collectionName, "{a : 1, b : -1}", indexName);
		//then
		List<DBObject> indexInfo = db1.getCollection(collectionName).getIndexInfo();
		String name = (String) indexInfo.get(1).get("name");
		DBObject key = (DBObject) indexInfo.get(1).get("key");
		boolean unique = (Boolean)indexInfo.get(1).get("unique");
		assertThat(name, is(indexName));
		assertThat(key.get("a").toString(), is("1"));
		assertThat(key.get("b").toString(), is("-1"));
		assertThat(unique, is(true));
	}
	
	@Test
	public void shouldEnsureIndexWithGivenName() {
		//given
		String collectionName = "testCol";
		String indexName = "myIndex";
		//when
		library.ensureIndexWithName(indexName, collectionName, "{a : 1, b : -1}");
		//then
		List<DBObject> indexInfo = db1.getCollection(collectionName).getIndexInfo();
		String name = (String) indexInfo.get(1).get("name");
		DBObject key = (DBObject) indexInfo.get(1).get("key");
		assertThat(name, is(indexName));
		assertThat(key.get("a").toString(), is("1"));
		assertThat(key.get("b").toString(), is("-1"));
	}
	
	@Test(expected = AssertionError.class)
	public void shouldFailIfDatabaseNotExists() {
		//given
		String database = "newDatabase";
		//when
		library.databaseShouldExist(database);
	}

	@Test
	public void shouldNotFailIfDatabaseExists() {
		//given
		String database = "robotdb1";
		mongoClient.getDB(database).createCollection("testCol", new BasicDBObject());
		//when
		library.databaseShouldExist(database);
	}
	
	@Test(expected = AssertionError.class)
	public void shouldFailIfCollectionNotExists() {
		//given
		String collectionName = "newCollection";
		//when
		library.collectionShouldExist(collectionName);
	}
	
	@Test
	public void shouldNotFailIfCollectionExists() {
		//given
		String collectionName = "testCol";
		db1.createCollection("testCol", new BasicDBObject());
		//when
		library.collectionShouldExist(collectionName);
	}
	
	@Test
	public void shouldPassIfDocumentExists() {
		//given
		db1.getCollection("testCol").insert(
				(DBObject) JSON.parse("{say : 'Hello MongoDb!'}"));
		//when
		library.documentShouldExist("testCol", "{say : 'Hello MongoDb!'}");
	}

	@Test(expected = AssertionError.class)
	public void shouldFailIfDocumentNotExists() {
		//given
		db1.getCollection("testCol").insert(
				(DBObject) JSON.parse("{say : 'Hello MongoDb!'}"));
		//when
		library.documentShouldExist("testCol", "{say : 'Hello MongoDb1!'}");
	}
	
	@Test
	public void shouldPassIfIndexExists() {
		//given
		db1.getCollection("testCol").ensureIndex((DBObject) JSON.parse("{a : 1, b : 1}"));
		//when
		library.indexShouldExist("testCol", "a_1_b_1");
	}

	@Test(expected = AssertionError.class)
	public void shouldFailIfIndexNotExists() {
		//given
		db1.getCollection("testCol").ensureIndex((DBObject) JSON.parse("{a : 1, b : 1}"));
		//when
		library.indexShouldExist("testCol", "a_1_b_11");
	}
	
	@Test
	public void shouldReturnAllDocumentsFromCollection() {
		//given
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		//when
		List<Map<String,Object>> allDocuments = library.getAllDocuments("testCol");
		//then
		assertThat(allDocuments.size(), is(2));
		assertThat((Integer)allDocuments.get(0).get("age"), is(22));
		assertThat((Integer)allDocuments.get(1).get("age"), is(23));
	}

	@Test
	public void shouldReturnDocumentsFromCollection() {
		//given
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Eric', age: 40}"));
		String json = "{ age : { $gte: 23 } }";
		//when
		List<Map<String,Object>> documents = library.getDocuments("testCol", json);
		//then
		assertThat(documents.size(), is(2));
		assertThat((Integer)documents.get(0).get("age"), is(23));
		assertThat((Integer)documents.get(1).get("age"), is(40));
	}

	@Test
	public void shouldRemoveDocumentsFromCollection() {
		//given
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Eric', age: 40}"));
		String json = "{ age : { $gte: 23 } }";
		//when
		library.removeDocuments("testCol", json);
		//then
		assertThat(db1.getCollection("testCol").count(), is(1L));
	}

	@Test
	public void shouldRemoveAllDocumentsFromCollection() {
		//given
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Eric', age: 40}"));
		//when
		library.removeAllDocuments("testCol");
		//then
		assertThat(db1.getCollection("testCol").count(), is(0L));
	}
	
	@Test
	public void shouldReturnCountFromCollection() {
		//given
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		//when
		long count = library.getCollectionCount("testCol");
		//then
		assertThat(count, is(2L));
	}
	
	@Test
	public void shouldReturnCollectionNames() {
		//given
		db1.getCollection("testCol1").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol2").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		//when
		List<String> collections = library.getCollections();
		//then
		assertThat(collections.size(), is(2));
	}

	@Test
	public void shouldReturnDatabaseNames() {
		//given
		db1.getCollection("testCol1").insert((DBObject) JSON.parse("{name : 'Max', age : 22}"));
		db1.getCollection("testCol2").insert((DBObject) JSON.parse("{name : 'Peter', age: 23}"));
		//when
		List<String> databases = library.getDatabases();
		//then
		assertThat(databases.size(), is(2));
	}
}
