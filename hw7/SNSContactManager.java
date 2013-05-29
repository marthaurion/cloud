import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;


public class SNSContactManager {
	private AmazonSimpleDB sdb;
	private AmazonS3 s3;
	private AmazonSNS sns;
	private String arn;
	private String domain;
	private String bucket;
	
	public SNSContactManager(AmazonSimpleDB s, AmazonS3 s3, AmazonSNS sn, String d, String b) {
		sdb = s;
		sns = sn;
		arn = "arn:aws:sns:us-west-2:623818142879:51083-updated";
		this.s3 = s3;
		domain = d;
		bucket = b;
	}
	
	//simply displays all properties of a given item
	public void details(String item) {
		String query = "SELECT * FROM "+domain+" WHERE itemName() = '"+item+"'";
		List<Item> res = sdb.select(new SelectRequest(query)).getItems();
		if(res.size() < 1) {
			System.out.println("No contacts found.");
			return;
		}
		List<Attribute> atts = res.get(0).getAttributes();
		for(int i = 0; i < atts.size(); i++) {
			System.out.println(atts.get(i).getName()+": "+atts.get(i).getValue());
		}
	}
	
	//method for listing all contacts from db
	public void listContacts(Scanner s) {
		System.out.println("1) List all contacts.");
		System.out.println("2) Search for certain contacts.");
		
		String in = s.nextLine();
		String query = "";
		if(in.contains("1")) query = "SELECT * FROM "+domain;
		//construct query based on input
		else if(in.contains("2")) {
			System.out.println("Search by: ");
			System.out.println("1) Start of first name");
			System.out.println("2) Start of last name");
			System.out.println("3) State");
			System.out.println("4) Zip Code");
			System.out.println("5) Tag");
			System.out.println("6) Birthday");
			
			in = s.nextLine();
			
			if(in.contains("1")) {
				System.out.println("Enter first few characters:");
				in = s.nextLine();
				query = "SELECT * FROM "+domain+" WHERE First_Name LIKE '"+in+"%'";
			}
			else if(in.contains("2")) {
				System.out.println("Enter first few characters:");
				in = s.nextLine();
				query = "SELECT * FROM "+domain+" WHERE Last_Name LIKE '"+in+"%'";
			}
			else if(in.contains("3")) {
				System.out.println("Enter two-letter state code:");
				in = s.nextLine();
				query = "SELECT * FROM "+domain+" WHERE State='"+in+"'";
			}
			else if(in.contains("4")) {
				System.out.println("Enter zip code:");
				in = s.nextLine();
				query = "SELECT * FROM "+domain+" WHERE Zip_Code='"+in+"'";
			}
			else if(in.contains("5")) {
				System.out.println("Enter tag name:");
				in = s.nextLine();
				query = "SELECT * FROM "+domain+" WHERE Tag='"+in+"'";
			}
			//for the birthday, give options
			else if(in.contains("6")) {
				System.out.println("1) Before");
				System.out.println("2) After");
				System.out.println("3) Between");
				in = s.nextLine();
				
				System.out.println("Enter date (format yyyy/mm/dd): ");
				String temp = s.nextLine();
				
				if(in.contains("1")) {
					query = "SELECT * FROM "+domain+" WHERE Birthday < '"+temp+"'";
				}
				else if(in.contains("2")) {
					query = "SELECT * FROM "+domain+" WHERE Birthday > '"+temp+"'";
				}
				else if(in.contains("3")) {
					query = "SELECT * FROM "+domain+" WHERE Birthday > '"+temp+"'";
					System.out.println("Enter end date (format yyyy/mm/dd: ");
					temp = s.nextLine();
					query = query+" AND Birthday < '"+temp+"'";
				}
				else return;
			}
			else return;
		}
		else return;
		SelectResult res = sdb.select(new SelectRequest(query));
		List<Item> temp = res.getItems();
		if(temp.size() == 0) {
			System.out.println("No contacts found.");
			return;
		}
		System.out.println("Results are in form \"itemname: firstname lastname\"");
		//go through every record
		for(int i = 0; i < temp.size(); i++) {
			//find the first name and last name attributes
			List<Attribute> att = temp.get(i).getAttributes();
			String name = temp.get(i).getName()+": ";
			String lname = null;
			
			for(int j = 0; j < att.size(); j++) {
				
				if(att.get(j).getName().equals("First_Name")) {
					name = name+att.get(j).getValue();
				}
				if(att.get(j).getName().equals("Last_Name")) {
					lname = att.get(j).getValue();
				}
			}
			
			//last name doesn't have to be set
			if(lname != null) name = name+" "+lname;
			
			System.out.println(name);
		}
	}
	
	//method for adding new contact
	public void add(Scanner s) throws Exception {
		List<ReplaceableAttribute> list = new ArrayList<ReplaceableAttribute>();
		System.out.println("Enter first name: ");
		String fname = s.nextLine();
		String lname = null;
		list.add(new ReplaceableAttribute("First_Name", fname, true));
		
		//allow the user to continuously add attributes
		String input = "";
		displayOptions();
		while(!input.equals("done")) {
			System.out.println("Enter an option number or type \"options\" to display the options again.");
			input = s.nextLine();
			if(input.charAt(0) == '1') {
				System.out.println("Enter last name: ");
				int x = indOf(list, "Last_Name");
				lname = s.nextLine();
				//if there is no last name field already
				if(x < 0) list.add(new ReplaceableAttribute("Last_Name", lname, true));
				//if there already is a last name to be added, replace it
				else list.set(x, list.set(x, list.get(x).withValue(s.nextLine())));
			}
			//both email and phone number just ask for the label, then the data itself
			else if(input.charAt(0) == '2') {
				System.out.println("Enter phone number label (home, cell, etc.): ");
				String label = s.nextLine();
				System.out.println("Enter phone number: ");
				list.add(new ReplaceableAttribute("Phone", label+": "+s.nextLine(), false));
			}
			else if(input.charAt(0) == '3') {
				System.out.println("Enter email address label (personal, work, etc.): ");
				String label = s.nextLine();
				System.out.println("Enter email address: ");
				list.add(new ReplaceableAttribute("Email", label+": "+s.nextLine(), false));
			}
			//ask for the address in pieces
			else if(input.charAt(0) == '4') {
				System.out.println("Enter street address (just the street number and street name): ");
				list.add(new ReplaceableAttribute("Street_Address", s.nextLine(), true));
				System.out.println("Enter city: ");
				list.add(new ReplaceableAttribute("City", s.nextLine(), true));
				System.out.println("Enter state (two letter abbrev): ");
				list.add(new ReplaceableAttribute("State", s.nextLine(), true));
				System.out.println("Enter zip code: ");
				list.add(new ReplaceableAttribute("Zip_Code", s.nextLine(), true));
			}
			else if(input.charAt(0) == '5') {
				System.out.println("Enter tag: ");
				list.add(new ReplaceableAttribute("Tags", s.nextLine(), false));
			}
			else if(input.charAt(0) == '6') {
				System.out.println("Enter birthday (format: yyyy/mm/dd): ");
				list.add(new ReplaceableAttribute("Birthday", s.nextLine(), true));
			}
			else if(input.contains("quit")) return;
			else if(input.contains("done")) break;
			else if(input.contains("options")) displayOptions();
			else System.out.println("Not a valid option.");
		}
		String item = fname+new Random().nextInt(10000);
		sdb.putAttributes(new PutAttributesRequest(domain, item, list));
		
		String filename = generateHtml(list, item);
		File F = new File(filename); 
		s3.putObject(new PutObjectRequest(bucket, filename, F).withCannedAcl(CannedAccessControlList.PublicRead));
		F.delete();
		System.out.println("Contacted Added.");
		
		
		PublishRequest req = new PublishRequest();
		req.setSubject("A new contact was added.");
		
		if(lname != null) fname = fname+" "+lname;
		req.setMessage("Contact "+fname+" was created. Check the page here: https://s3.amazonaws.com/"+bucket+"/"+filename);
		req.setTopicArn(arn);
		
		sns.publish(req);
	}
	
	public void edit(Scanner s, String name) throws Exception {
		String query = "SELECT * FROM "+domain+" WHERE itemName() = '"+name+"'";
		List<Item> res = sdb.select(new SelectRequest(query)).getItems();
		if(res.size() < 1) {
			System.out.println("No contacts found.");
			return;
		}
		
		String oldname = res.get(0).getName();
		
		List<Attribute> atts = res.get(0).getAttributes();
		List<ReplaceableAttribute> list = new ArrayList<ReplaceableAttribute>();
		
		for(int i = 0; i < atts.size(); i++) {
			list.add(new ReplaceableAttribute(atts.get(i).getName(), atts.get(i).getValue(), false));
		}
		
		int x = indOf(list, "First_Name");
		if(x != 0) list.add(0, list.remove(x));
		
		//first display the current contact details before editing
		System.out.println("Old Contact Details");
		details(oldname);
		System.out.println();
		
		String input = "";
		while(!input.equals("done")) {
			System.out.println("Select contact details to edit: ");
			System.out.println("1) First Name");
			System.out.println("2) Last Name");
			System.out.println("3) Phone Number");
			System.out.println("4) Email Address");
			System.out.println("5) Address");
			System.out.println("6) Tags");
			System.out.println("7) Birthday");
			System.out.println("Type \"quit\" to cancel.");
			System.out.println("Type \"done\" to commit changes.");
			input = s.nextLine();
			
			if(input.contains("1")) {
				System.out.println("New first name: ");
				list.set(0, new ReplaceableAttribute("First_Name", s.nextLine(), true));
			}
			else if(input.contains("2")) {
				System.out.println("New last name: ");
				//first remove the old one
				x = indOf(list, "Last_Name");
				if(x >= 0) list.remove(x);
				//now add the new entry
				list.add(new ReplaceableAttribute("Last_Name", s.nextLine(), true));
			}
			else if(input.contains("3")) {
				System.out.println("1) Add new number.");
				System.out.println("2) Remove number.");
				
				input = s.nextLine();
				if(input.contains("1")) {
					System.out.println("Enter phone number label (home, cell, etc.): ");
					String label = s.nextLine();
					System.out.println("Enter phone number: ");
					list.add(new ReplaceableAttribute("Phone", label+": "+s.nextLine(), false));
				}
				else if(input.contains("2")) {
					System.out.println("Enter label of phone to remove: ");
					String label = s.nextLine();
					for(int i = 0; i < list.size(); i++) {
						if(list.get(i).getName().equals("Phone") && 
								list.get(i).getValue().contains(label)) {
							
							list.remove(i);
							break;
						}
					}
				}
				else continue;
			}
			else if(input.contains("4")) {
				System.out.println("1) Add new email.");
				System.out.println("2) Remove email.");
				
				input = s.nextLine();
				if(input.contains("1")) {
					System.out.println("Enter email address label (personal, work, etc.): ");
					String label = s.nextLine();
					System.out.println("Enter email address: ");
					list.add(new ReplaceableAttribute("Email", label+": "+s.nextLine(), false));
				}
				else if(input.contains("2")) {
					System.out.println("Enter label of email to remove: ");
					String label = s.nextLine();
					for(int i = 0; i < list.size(); i++) {
						if(list.get(i).getName().equals("Email") && 
								list.get(i).getValue().equals(label)) {
						
							list.remove(i);
							break;
						}
					}
				}
				else continue;
			}
			else if(input.contains("5")) {
				//first remove the old version
				for(int i = 0; i < list.size(); i++) {
					String prop = list.get(i).getName();
					if(prop.equals("Street_Address") || prop.equals("City") ||
							prop.equals("State") || prop.equals("Zip_Code")) {
						
						list.remove(i);
					}
				}
				
				System.out.println("Enter street address (just the street number and street name): ");
				list.add(new ReplaceableAttribute("Street_Address", s.nextLine(), true));
				System.out.println("Enter city: ");
				list.add(new ReplaceableAttribute("City", s.nextLine(), true));
				System.out.println("Enter state (two letter abbrev): ");
				list.add(new ReplaceableAttribute("State", s.nextLine(), true));
				System.out.println("Enter zip code: ");
				list.add(new ReplaceableAttribute("Zip_Code", s.nextLine(), true));
			}
			else if(input.contains("6")) {
				System.out.println("1) Add new tag.");
				System.out.println("2) Remove tag.");
				
				input = s.nextLine();
				if(input.contains("1")) {
					System.out.println("Enter tag name: ");
					list.add(new ReplaceableAttribute("Tags", s.nextLine(), false));
				}
				else if(input.contains("2")) {
					System.out.println("Enter tag name to remove: ");
					String tag = s.nextLine();
					for(int i = 0; i < list.size(); i++) {
						if(list.get(i).getName().equals("Tags") && 
								list.get(i).getValue().equals(tag)) {
							
							list.remove(i);
						}
					}
				}
				else continue;
			}
			else if(input.contains("7")) {
				//remove old version
				for(int i = 0; i < list.size(); i++) {
					String prop = list.get(i).getName();
					if(prop.equals("Birthday")) {
						list.remove(i);
					}
				}
				
				System.out.println("Enter birthday (format: yyyy/mm/dd): ");
				list.add(new ReplaceableAttribute("Birthday", s.nextLine(), true));
			}
			else if(input.contains("quit")) return;
			else if(input.contains("done")) continue;
			else return;
		}
		
		s3.deleteObject(bucket, oldname+"_contact.html");
		sdb.deleteAttributes(new DeleteAttributesRequest(domain, oldname));
		
		x = indOf(list, "First_Name");
		String item = list.get(x).getValue()+new Random().nextInt(10000);
		sdb.putAttributes(new PutAttributesRequest(domain, item, list));
		
		String filename = generateHtml(list, item);
		File F = new File(filename); 
		s3.putObject(new PutObjectRequest(bucket, filename, F).withCannedAcl(CannedAccessControlList.PublicRead));
		F.delete();
		System.out.println("Contacted Updated.");
		
		PublishRequest req = new PublishRequest();
		req.setSubject("A contact was updated.");
		
		String fname = list.get(indOf(list, "First_Name")).getValue();
		x = indOf(list, "Last_Name");
		if(x >= 0) fname = fname+" "+list.get(x).getValue();
		req.setMessage("Contact "+fname+" was updated. Check the page here: https://s3.amazonaws.com/"+bucket+"/"+filename);
		req.setTopicArn(arn);
		
		sns.publish(req);
	}
	
	//some helper functions
	private int indOf(List<ReplaceableAttribute> list, String name) {
		for(int i = 0; i < list.size(); i++) {
			if(list.get(i).getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}
	
	private void displayOptions() {
		System.out.println("Select contact details to add from the following: ");
		System.out.println("1) Last Name");
		System.out.println("2) Phone Number");
		System.out.println("3) Email Address");
		System.out.println("4) Address");
		System.out.println("5) Tags");
		System.out.println("6) Birthday");
		System.out.println("Type \"quit\" to cancel.");
		System.out.println("Type \"done\" to commit changes.");
	}
	
	private String generateHtml(List<ReplaceableAttribute> list, String item) throws IOException {
		String filename = item+"_contact.html";
    	BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		//headers
		writer.write("<table>");
		writer.write("<tr><th>First Name</th>");
		
		if(indOf(list, "Last_Name") > -1) writer.write("<th>Last Name</th>");
		if(indOf(list, "Phone") > -1) writer.write("<th>Phone Numbers</th>");
		if(indOf(list, "Email") > -1) writer.write("<th>Email Addresses</th>");
		if(indOf(list, "Street_Address") > -1) writer.write("<th>Address</th>");
		if(indOf(list, "Tags") > -1) writer.write("<th>Tags</th>");
		if(indOf(list, "Birthday") > -1) writer.write("<th>Birthday</th>");
		
		writer.write("</tr>");
		writer.newLine();
		//contact info
		writer.write("<tr><td>"+list.get(indOf(list, "First_Name")).getValue()+"</td>"); //first name will always be in the list
		
		if(indOf(list, "Last_Name") > -1) writer.write("<td>"+list.get(indOf(list, "Last_Name")).getValue()+"</td>");
		if(indOf(list, "Phone") > -1) writeAll(writer, "Phone", list);
		if(indOf(list, "Email") > -1) writeAll(writer, "Email", list);
		if(indOf(list, "Street_Address") > -1) {
			writer.write("<td>"+list.get(indOf(list, "Street_Address")).getValue()+"<br>");
			writer.write(list.get(indOf(list, "City")).getValue()+", "+list.get(indOf(list, "State")).getValue());
			writer.write(" "+list.get(indOf(list, "Zip_Code")).getValue()+"</td>");
		}
		if(indOf(list, "Tags") > -1) writeAll(writer, "Tags", list);
		if(indOf(list, "Birthday") > -1) writer.write("<td>"+list.get(indOf(list, "Birthday")).getValue()+"</td>");
		writer.write("</tr></table>");
		writer.newLine();
		writer.flush();
		writer.close();
		
		return filename;
	}
	
	//writes all values matching a given attribute name in a list
	private void writeAll(BufferedWriter writer, String name, List<ReplaceableAttribute> list) throws IOException {
		writer.write("<td>");
		boolean first = true;
		for(int i = 0; i < list.size(); i++) {
			if(list.get(i).getName().equals(name)) {
				if(first) first = false;
				else writer.write("<br>");
				writer.write(list.get(i).getValue());
			}
		}
		writer.write("</td>");
	}
}
