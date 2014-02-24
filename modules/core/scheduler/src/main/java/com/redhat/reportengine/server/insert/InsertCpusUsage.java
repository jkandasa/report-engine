package com.redhat.reportengine.server.insert;

import java.sql.SQLException;
import java.util.Date;

import org.apache.log4j.Logger;
import org.hyperic.sigar.CpuPerc;

import com.redhat.reportengine.agent.rest.mapper.UsageCpus;
import com.redhat.reportengine.server.collection.DynamicTable;
import com.redhat.reportengine.server.dbdata.ResourceCpuTable;
import com.redhat.reportengine.server.dbmap.DynamicTableName.TYPE;
import com.redhat.reportengine.server.dbmap.ResourceCpu;


/**
 * @author jkandasa@redhat.com (Jeeva Kandasamy)
 * Jul 18, 2013
 */
public class InsertCpusUsage implements Runnable {
	private static Logger _logger = Logger.getLogger(InsertCpusUsage.class);
	private int serverId;
	private UsageCpus usageCpus;

	public InsertCpusUsage(){
		super();
	}

	public InsertCpusUsage(int serverId, UsageCpus usageCpus){
		this.serverId = serverId;
		this.usageCpus = usageCpus;
	}

	private ResourceCpu getResourceCpu(CpuPerc cpuPerc, ResourceCpu resourceCpu){
		resourceCpu.setCombined((float) cpuPerc.getCombined());
		resourceCpu.setIdle((float) cpuPerc.getIdle());
		resourceCpu.setIrq((float) cpuPerc.getIrq());
		resourceCpu.setNice((float) cpuPerc.getNice());
		resourceCpu.setSoftIrq((float) cpuPerc.getSoftIrq());
		resourceCpu.setStolen((float) cpuPerc.getStolen());
		resourceCpu.setSys((float) cpuPerc.getSys());
		resourceCpu.setUser((float) cpuPerc.getUser());
		resourceCpu.setWait((float) cpuPerc.getWait());
		return resourceCpu;
	}

	private void insert(ResourceCpu resourceCpu){
		ResourceCpuTable resourceCpuTable = new ResourceCpuTable();
		//For CPUs usage
		for(int i=0; i<usageCpus.getCpus().length;i++){
			try {
				//resourceCpu.setTableSubName(ResourceCpuTable.getMultiCpuSubName(serverId, i));
				resourceCpu.setTableSubName(String.valueOf(DynamicTable.get(ResourceCpuTable.getMultiCpuSubName(serverId, i), serverId, TYPE.CPUS).getId()));
				resourceCpu.setRemoteTime(new Date(usageCpus.getTime()));

				resourceCpuTable.add(getResourceCpu(usageCpus.getCpus()[i], resourceCpu));
			} catch (SQLException ex) {
				if(ex.getMessage().contains("does not exist")){
					try {
						resourceCpuTable.createTable(resourceCpu.getTableSubName());
						resourceCpuTable.add(resourceCpu);
					} catch (SQLException e) {
						_logger.error("Exception, ", ex);
					}

				}else{
					_logger.error("Exception, ", ex);

				}
			}
		} 
	}

	public void updateCpusUsage(int serverId, UsageCpus usageCpus){
		ResourceCpu resourceCpu = new ResourceCpu();
		resourceCpu.setLocalTime(new Date());

		// For CPU usage
		resourceCpu.setRemoteTime(new Date(usageCpus.getTime()));
		new InsertCpuUsage(this.serverId).insert(getResourceCpu(usageCpus.getCpu(), resourceCpu));

		//For CPUs usage
		insert(resourceCpu);
	}

	@Override
	public void run() {
		this.updateCpusUsage(this.serverId, this.usageCpus);		
	}
}
