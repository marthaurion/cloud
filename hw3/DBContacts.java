import java.util.List;
import java.util.Scanner;

import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.sun.corba.se.spi.orbutil.fsm.Input;


public class DBContacts {
	public static void main(String[] args) throws Exception {
		AmazonSimpleDB sdb = new AmazonSimpleDBClient(new ClasspathPropertiesFileCredentialsProvider());
		AmazonS3 s3 = new AmazonS3Client(new ClasspathPropertiesFileCredentialsProvider());
		Scanner sc = new Scanner(System.in);
		
		//just my default
		Region reg = Region.getRegion(Regions.US_WEST_2);
		
		System.out.println("Select region: ");
		System.out.println("1) US East");
		System.out.println("2) US West 1");
		System.out.println("3) US West 2");
		String input = sc.nextLine();
		if(input.contains("1")) {
			reg = Region.getRegion(Regions.US_EAST_1);
		}
		else if(input.contains("2")) {
			reg = Region.getRegion(Regions.US_WEST_1);
		}
		else if(input.contains("3")) {
			reg = Region.getRegion(Regions.US_WEST_2);
		}
		else System.exit(0);
		sdb.setRegion(reg);
		
		String domain = "Contact_Manager";
		
		//creates the domain if it doesn't exist
		ListDomainsResult dres = sdb.listDomains();
		//account for the case where there are no domains
		if(dres == null) sdb.createDomain(new CreateDomainRequest(domain));
		else {
			List<String> domains = dres.getDomainNames();
			boolean flag = false;
			for(int i = 0; i < domains.size(); i++) {
				if(domains.get(i).equals(domain)) {
					flag = true;
				}
			}
			if(!flag) sdb.createDomain(new CreateDomainRequest(domain));
		}
		
		System.out.println("Current buckets: ");
		List<Bucket> bucks = s3.listBuckets();
		if(bucks.size() > 0) {
			for(int i = 0; i < bucks.size(); i++) {
				System.out.println(bucks.get(i).getName());
			}
		}
		
		System.out.println("Enter requested bucket name: ");
		String bname = sc.nextLine();
		
		//check bucket
		if(s3.doesBucketExist(bname)) {
			boolean flag = false;
            for (Bucket bucket : s3.listBuckets()) {
            	if(bucket.getName().equals(bname)) flag = true;
            }
            if(flag == false) {
            	System.out.println("Bucket name exists.");
            	System.exit(0);
            }
            System.out.println("Using existing bucket "+bname);
		}
		else {
			//try to create bucket
			//should default to US Standard
			s3.createBucket(bname);
		}
		
		DBContactManager manage = new DBContactManager(sdb, s3, domain, bname);
		
		input = "";
		
		//use same code for options
		boolean options = true;
		
		while(!input.contains("quit")) {
			if(options) {
				System.out.println("1) List contacts");
				System.out.println("2) View contact details");
				System.out.println("3) Edit contact");
				System.out.println("4) Add new contact");
				System.out.println("Type \"quit\" to exit");
				System.out.println("Or enter a number for an option above.");
				options = false;
			}
			else {
				System.out.println("Enter an option number or type \"options\" to display the options again.");
			}
			
			input = sc.nextLine();
			
			if(input.length() == 0 || input.contains("quit")) continue; 
			else if(input.contains("1")) manage.listContacts(sc);
			else if(input.contains("2")) {
				System.out.println("Enter contact item name:");
				manage.details(sc.nextLine());
			}
			else if(input.contains("3")) {
				System.out.println("Enter contact item name:");
				manage.edit(sc, sc.nextLine());
			}
			else if(input.contains("4")) manage.add(sc);
			else if(input.contains("options")) options = true;
			else {
				System.out.println("Incorrect input.");
			}
		}
		
		
		sc.close();
	}
}
