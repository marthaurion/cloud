import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.*;
import java.io.*;

public class Contact {
	public static void main(String[] args) throws Exception {
		AmazonS3 s3 = new AmazonS3Client(new ClasspathPropertiesFileCredentialsProvider());
		
		String bname, input;
		String fname, lname, phone;
		//get user input for bucket name
		System.out.println("Enter requested bucket name: ");
		Scanner sc = new Scanner(System.in);
		
		bname = sc.nextLine();
		
		
		try {
			//uses the existing bucket if it exists in this account
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
			input = "";
			
			while(!input.contains("quit")) {
				System.out.println("1) List bucket contents");
				System.out.println("2) Delete object in bucket");
				System.out.println("3) Create new object in bucket");
				System.out.println("4) Edit object in bucket");
				System.out.println("Type \"quit\" to exit");
				System.out.println("Or enter a number for an option above");
				
				input = sc.nextLine();
				
				if(input.charAt(0) == '1') {
					ObjectListing objectListing = s3.listObjects(bname);
					for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
						System.out.println(objectSummary.getKey());
					}
				}
				else if(input.charAt(0) == '2') {
					System.out.println("Enter object name to delete:");
					String oname = sc.nextLine();
					if(ObExists(s3, bname, oname)) {
						s3.deleteObject(bname, oname);
					}
					else {
						System.out.println("Object does not exist.");
					}
				}
				else if(input.charAt(0) == '3') {
					System.out.println("Creating new contact");
					System.out.println("Enter contact first name: ");
					fname = sc.nextLine();
					System.out.println("Enter contact last name: ");
					lname = sc.nextLine();
					System.out.println("Enter contact phone number: ");
					phone = sc.nextLine();
					
					//write HTML file from input
					String filename = writeHTML(fname, lname, phone);
					File F = new File(filename); 
					s3.putObject(bname, filename, F);
					F.delete();
				}
				else if(input.charAt(0) == '4') {
					System.out.println("Object name to be edited: ");
					String filename = sc.nextLine();
					//first check if object exists
					if(!ObExists(s3, bname, filename)) {
						System.out.println("Object doesn't exist.");
						continue;
					}
					
					S3Object ob = s3.getObject(bname, filename);
					Scanner scan = new Scanner(ob.getObjectContent());
					String[] contacts = parseHTML(scan);

					int ind = -1;
					String in = "";
					while(!in.equals("done")) {
						//let the user make as many edits as they want
						//before committing all changes
						System.out.println("Editing contact");
						System.out.println("1) First name: "+contacts[0]);
						System.out.println("2) Last name: "+contacts[1]);
						System.out.println("3) Phone number: "+contacts[2]);
						System.out.println("Enter number for field to edit");
						System.out.println("or \"done\" to commit changes.");
						
						in = sc.nextLine();
						if(in.equals("done")) continue;
						else if(in.charAt(0) == '1') ind = 0;
						else if(in.charAt(0) == '2') ind = 1;
						else if(in.charAt(0) == '3') ind = 2;
						else {
							System.out.println("Invalid input.");
							continue;
						}
						//should only reach here if there is a value to be edited
						System.out.println("Enter new value: ");
						contacts[ind] = sc.nextLine();
					}
					
					//delete old object and upload a new one
					s3.deleteObject(bname, filename);
					filename = writeHTML(contacts[0], contacts[1], contacts[2]);
					File F = new File(filename); 
					s3.putObject(bname, filename, F);
					F.delete();
				}
				else if(input.contains("quit")) continue;
				else {
					System.out.println("Incorrect input.");
				}
			}
			
		}
		//copied from example code
		catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with S3, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
		
		
		sc.close();
	}
	
	//returns filename of created file
	public static String writeHTML(String fname, String lname, String phone) throws Exception {
		String filename = fname+"_"+lname+"_"+"contact.html";
        	BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		//logo
		writer.write("<img src=\"http://d1hv9dd9trfzpd.cloudfront.net/5098041632065428791.png\">");
		//headers
		writer.write("<table>");
		writer.write("<tr><th>First Name</th><th>Last Name</th><th>Phone Number</th></tr>");
		writer.newLine();
		//contact info
		writer.write("<tr><td>"+fname+"</td><td>"+lname+"</td><td>"+phone);
		writer.write("</td></tr></table>");
		writer.newLine();
		writer.flush();
		writer.close();
		
		return filename;
	}
	
	//returns an array of strings with first name, last name, and phone number
	public static String[] parseHTML(Scanner S) throws FileNotFoundException {
		String[] out = {"", "", ""};
		if(S.hasNext()) {
			S.nextLine(); //first line can be ignored
			String noHTML = S.nextLine().replaceAll("\\<.*?\\>", "|");
			S.close();
			//pull the contact information into the output array
			int j = 0;
			for(int i = 0; i < 3; i++) {
				while(noHTML.charAt(j) == '|') {
					j++;
				}
				while(noHTML.charAt(j) != '|') {
					out[i] += noHTML.charAt(j);
					j++;
				}
			}
			return out;
		}
		else {
			System.out.println("Error: File is empty");
			return null;
		}
	}
	
	//simple method to check whether an object exists in the bucket 'bname'
	//with the name 'name'
	public static boolean ObExists(AmazonS3 s3, String bname, String name) {
		ObjectListing objectListing = s3.listObjects(bname);
		for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
			if(objectSummary.getKey().equals(name)) return true;
		}
		return false;
	}
}
