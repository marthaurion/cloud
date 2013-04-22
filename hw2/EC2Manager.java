import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.ec2.model.VolumeAttachment;


public class EC2Manager {
	private AmazonEC2 ec2;
	private Instance inst;
	
	public EC2Manager(AmazonEC2 ec) {
		ec2 = ec;
		inst = null;
	}
	
	//it seems convoluted to get a list of instances, so I generalized it into a single function
	public List<Instance> getInstances() {
		List<Reservation> list = ec2.describeInstances().getReservations();
		ArrayList<Instance> instance = new ArrayList<Instance>();
		if(list.size() == 0) {
			//if there are no instances, return null
			System.out.println("Error: no instances found.");
			return null;
		}
		else {
			//if there are instances available, add them to a list and return that list
			for(int i = 0; i < list.size(); i++) {
				instance.addAll(list.get(i).getInstances());
			}
		}
		
		return instance;
	}
	
	//checks volumes attached to currently set instance
	public void listVolumes() {
		List<Volume> vols = getAttachedVolumes();
		if(vols != null) {
			for(int i = 0; i < vols.size(); i++) {
				System.out.println(vols.get(i).getVolumeId());
			}
		}
	}
	
	public void listFreeVolumes() {
		List<Volume> vols = getDetachedVolumes();
		if(vols != null) {
			for(int i = 0; i < vols.size(); i++) {
				System.out.println(vols.get(i).getVolumeId());
			}
		}
	}
	
	//generic function to return a list of volumes attached to current instance
	public List<Volume> getAttachedVolumes() {
		if(inst == null) {
			System.out.println("Error: no instance set.");
			return null;
		}
		else {
			List<Volume> vols = ec2.describeVolumes().getVolumes();
			List<Volume> toReturn = new ArrayList<Volume>();
			//first check if there are any volumes at all
			if(vols.size() > 0) {
				for(int i = 0; i < vols.size(); i++) {
					List<VolumeAttachment> attach = vols.get(i).getAttachments();
					if(attach.size() > 0) {
						for(int j = 0; j < attach.size(); j++) {
							//only print out the volume's id if the attachments include the set instance id
							if(attach.get(j).getInstanceId().equals(inst.getInstanceId())) {
								toReturn.add(vols.get(i));
								break;
							}
							//endif
						}
						//endfor
					}
					//endif
				}
				return toReturn;
			}
			System.out.println("No attached volumes.");
			return null;
		}
	}
	
	//mostly a copy from above
	//finds volumes attached to no instance
	public List<Volume> getDetachedVolumes() {
		if(inst == null) {
			System.out.println("Error: no instance set.");
			return null;
		}
		else {
			List<Volume> vols = ec2.describeVolumes().getVolumes();
			List<Volume> toReturn = new ArrayList<Volume>();
			//first check if there are any volumes at all
			if(vols.size() > 0) {
				for(int i = 0; i < vols.size(); i++) {
					List<VolumeAttachment> attach = vols.get(i).getAttachments();
					if(attach.size() == 0) {
						toReturn.add(vols.get(i));
					}
				}
				return toReturn;
			}
			System.out.println("No attached volumes.");
			return null;
		}
	}
	
	public void detachVolume(String volumeId) {
		List<Volume> vols = getAttachedVolumes();
		if(vols != null) {
			for(int i = 0; i < vols.size(); i++) {
				//if the volume id is found, detach it
				if(vols.get(i).getVolumeId().equals(volumeId)) {
					ec2.detachVolume(new DetachVolumeRequest(volumeId));
					return;
				}
			}
			//only reach here if volume id is not found
			System.out.println("Error: volume id not found.");
		}
	}
	
	public void attachVolume(String volumeId) {
		List<Volume> attach = getAttachedVolumes();
		List<Volume> free = getDetachedVolumes();
		Volume toAdd = null;
		//first find the volume in the free volumes
		if(free != null) {
			for(int i = 0; i < free.size(); i++) {
				if(free.get(i).getVolumeId().equals(volumeId)) {
					toAdd = free.get(i);
					break;
				}
			}
			//only null if no volumes match the volume id
			if(toAdd == null) {
				System.out.println("Error: volume not found.");
				return;
			}
			//now we need to determine a device name to attach
			//the number of attached volumes should only be 1 if there is only the root attached
			if(attach.size() == 1) {
				ec2.attachVolume(new AttachVolumeRequest(toAdd.getVolumeId(), inst.getInstanceId(), "/dev/sdf"));
			}
			//if there are attached volumes, we need to create an available device name
			else {
				//first get all device names
				List<String> names = new ArrayList<String>();
				for(int i = 0; i < attach.size(); i++) {
					List<VolumeAttachment> temp = attach.get(i).getAttachments();
					for(int j = 0; j < temp.size(); j++) {
						if(temp.get(j).getInstanceId().equals(inst.getInstanceId())) {
							names.add(temp.get(j).getDevice());
						}
					}
				}
				//this will only work until you run out of alphabet letters
				//not the best system, but it gives you ~20 volumes
				Collections.sort(names);
				String dev = names.get(names.size()-1);
				char c = dev.charAt(dev.length()-1);
				c++;
				ec2.attachVolume(new AttachVolumeRequest(toAdd.getVolumeId(), inst.getInstanceId(), dev.substring(0, dev.length()-1) + c));
				System.out.println("Volume attached.");
			}
		}
		else {
			System.out.println("Error: no free volumes found.");
		}
	}
	
	//lists all instances
	public void listInstances() {
		List<Instance> instance = getInstances();
		if(instance != null) {
			for(int i = 0; i < instance.size(); i++) {
				System.out.println(instance.get(i).getInstanceId());
			}
		}
	}
	
	//searches for an instance with the instance id, saves it in class variable and returns it
	public Instance setInstance(String id) {
		ArrayList<Instance> instance = new ArrayList<Instance>(getInstances());
		for(int i = 0; i < instance.size(); i++) {
			if(instance.get(i).getInstanceId().equals(id)) {
				inst = instance.get(i);
				return inst;
			}
		}
		System.out.println("Error: Instance not found.");
		return null;
	}
	
}
