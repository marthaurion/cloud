//import com.amazonaws.auth.BasicAWSCredentials;

import java.util.Scanner;

import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.regions.Region;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;



public class ec2instance {
	public static void main(String[] args) {
		//comment out one of the next two lines depending on whether environmental variables or a credentials file is being used
		// AmazonEC2 ec2 = new AmazonEC2Client(new BasicAWSCredentials(System.getenv("AWS_ACCESS_KEY"), System.getenv("AWS_SECRET_KEY")));
		AmazonEC2 ec2 = new AmazonEC2Client(new ClasspathPropertiesFileCredentialsProvider());
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		ec2.setRegion(usWest2);
		
		EC2Manager manage = new EC2Manager(ec2);
		
		Scanner scan = new Scanner(System.in);
		
		System.out.println("Available instances");
		manage.listInstances();
		
		System.out.println("Select instance");
		//set instance based on user input
		String temp = scan.nextLine();
		Instance ins = manage.setInstance(temp);
		
		if(ins == null) {
			System.out.println("Invalid instance.");
			System.exit(0);
		}
		
		String input = "";
		boolean options = true;
		while(!input.contains("quit")) {
			if(options) {
				System.out.println("1) List attached volumes");
				System.out.println("2) List free volumes");
				System.out.println("3) Attach volume");
				System.out.println("4) Detach volume");
				System.out.println("Type \"quit\" to exit");
				System.out.println("Or enter a number for an option above.");
				options = false;
			}
			else {
				System.out.println("Enter an option number or type \"options\" to display the options again.");
			}
			input = scan.nextLine();
			
			if(input.charAt(0) == '1') manage.listVolumes();
			else if(input.charAt(0) == '2') manage.listFreeVolumes();
			else if(input.charAt(0) == '3') {
				System.out.println("Enter volume ID:");
				manage.attachVolume(scan.nextLine());
			}
			else if(input.charAt(0) == '4') {
				System.out.println("Enter volume ID:");
				manage.detachVolume(scan.nextLine());
			}
			else if(input.contains("quit")) continue;
			else if(input.contains("options")) options = true;
			else {
				System.out.println("Incorrect input.");
			}
		}
		
		scan.close();
	}
}
