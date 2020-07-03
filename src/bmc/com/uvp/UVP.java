package bmc.com.uvp;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import java.util.ResourceBundle;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;


import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

public class UVP {
	private static String token;

	public void getToken(String url, String key) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(url);
		try {
			httppost.addHeader("X-Auth-UserType", "0");
			httppost.addHeader("X-Auth-User", "administrator");
			httppost.addHeader("X-Auth-Key", key);
			HttpResponse response = httpclient.execute(httppost);
			token = response.getHeaders("X-Auth-Token")[0].getValue();
			//System.out.println(token);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			httppost.releaseConnection();
			httppost.reset();
		}
	}
	
	public String doGet(String url, String key) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet httpget = new HttpGet(url);
		String getResult = "";
		try {
			httpget.addHeader("X-Auth-UserType", "0");
			httpget.addHeader("X-Auth-Token", key);
			httpget.addHeader("Accept","application/json;version=1.1;charset=UTF-8");
			HttpResponse response = httpclient.execute(httpget);
			//httpexitcode = response.getStatusLine().getStatusCode();
			// System.out.println(httpexitcode);
			//InputStream in = response.getEntity().getContent();
			//byte[] bs = new byte[1024000];
            //in.read(bs);
            //getResult = new String(bs,"utf-8");
			BufferedReader bs = new BufferedReader(new InputStreamReader(response.getEntity().getContent(),"UTF-8"));
			getResult =bs.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			httpget.releaseConnection();
			httpget.reset();
		}
		return getResult;
	}
	//post 方法
	public String doPost(String url,String key,String body_urn){
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost postRequest = new HttpPost(url);
		String output = null;
		try {
			postRequest.addHeader("X-Auth-UserType", "0");
			postRequest.addHeader("Content-Type", "application/json");
			postRequest.addHeader("X-Auth-Token", key);
			postRequest.addHeader("Accept","application/json;version=1.1;charset=UTF-8");
			StringEntity input = new StringEntity("[{\"urn\":\""+body_urn+"\", \"metricId\":[\"cpu_usage\",\"mem_usage\",\"nic_byte_in\",\"nic_byte_out\",\"disk_io_in\",\"disk_io_out\"]}]");
			postRequest.setEntity(input);
			HttpResponse response = httpclient.execute(postRequest);
			if(response.getStatusLine().getStatusCode()!=200){
				//throw new RuntimeException("Failed:HTTP error code:"+response.getStatusLine().getStatusCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			output = br.readLine();
			//System.out.println("output from post:");
			//System.out.println(output);
			/*
			while((output=br.readLine())!=null){
				System.out.println(output);
			}
			*/	
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			postRequest.releaseConnection();
			postRequest.reset();	
		}
		return output;
	}
	
	public static void main(String[] args) {
		ResourceBundle conf = ResourceBundle.getBundle("bmc/com/uvp/init");
		UVP uvp = new UVP();
		String passwd_raw = conf.getString("password");
		String passwd = uvp.getPasswd(passwd_raw);
		String url = conf.getString("url");
		uvp.getToken(url+"/service/session",passwd);
		//System.out.println(token);
		
		System.out.println("TS;DURATION;SYSNM;OBJNM;SUBOBJNM;VALUE;DS_SYSNM");
		//获取站点信息
		String siteInfo = uvp.doGet(url+"/service/sites", token);
		//System.out.println(siteName);
		JSONObject sitejson = JSONObject.fromObject(siteInfo);
		JSONArray sitearray = sitejson.getJSONArray("sites");
		String siteUrl=sitearray.getJSONObject(0).getString("uri");
		//System.out.println(url+siteUrl);
		
		//获取cluster实例信息:url,urn,uri
		String clusterInfo = uvp.doGet(url+siteUrl+"/clusters", token);
		//System.out.println(clusterInfo);
		JSONObject clujson = JSONObject.fromObject(clusterInfo);
		JSONArray cluarray = clujson.getJSONArray("clusters");
		//System.out.println(cluarray.size());
		for(int cl=0;cl<cluarray.size();cl++){
			String cluUrn = cluarray.getJSONObject(cl).getString("urn");
			//System.out.println(cluUrn);
			//获取cluster性能
			String clusterPef = uvp.doPost(url+siteUrl+"/monitors/objectmetric-realtimedata",token,cluUrn);
			uvp.getKpiPef(clusterPef);
			
			//获取虚拟机实例
			String vmInfo = uvp.doGet(url+siteUrl+"/vms?detail=2&scope="+cluUrn, token);
			//System.out.println(vmInfo);
			JSONObject vmjson = JSONObject.fromObject(vmInfo);
			JSONArray vmarray = vmjson.getJSONArray("vms");
			//System.out.println(vmarray.size());
			for(int vml=0;vml<vmarray.size();vml++){
				//获取vm的urn 
				String vmUrn = vmarray.getJSONObject(vml).getString("urn");
				//System.out.println(vmUrn);
				//根据vm的urn调用post 获取其性能
				String vmPerf = uvp.doPost(url+siteUrl+"/monitors/objectmetric-realtimedata",token,vmUrn);
				uvp.getKpiPef(vmPerf);				
			}
			
			
		}
		
		//System.out.println("Cluster Name:"+clusterInst);
		//System.out.println("cluster urn:"+clusterUrn);
		
		
		//获取物理机实例
		String hostInfo = uvp.doGet(url+siteUrl+"/hosts", token);
		//System.out.println(hostInfo);
		JSONObject json = JSONObject.fromObject(hostInfo);
		JSONArray arrays = json.getJSONArray("hosts");
		//System.out.println(arrays.size());
		for(int i=0;i<arrays.size();i++){
			//System.out.println(arrays.get(i));
			String hostUrn = arrays.getJSONObject(i).getString("urn");
			//System.out.println(hostUrn);
	    			//获取物理机性能
	    			String hostPerf = uvp.doPost(url+siteUrl+"/monitors/objectmetric-realtimedata",token,hostUrn);
	    			uvp.getKpiPef(hostPerf);
	    }
		
	 }
	public void getKpiPef(String getdata){
		String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
		//System.out.println(timestamp);
		JSONObject host_json = JSONObject.fromObject(getdata);
		JSONArray host_arrays = host_json.getJSONArray("items");
		
		for(int j =0 ;j<host_arrays.size();j++){
	    	String objectName = host_arrays.getJSONObject(j).getString("objectName");
	    	String values = host_arrays.getJSONObject(j).getString("value");
	    	JSONArray vs = JSONArray.fromObject(values);
	    	//System.out.println(objectName+",");
	    	
	    	//System.out.print("CPU_UTIL"+)
	    	String re = timestamp+";300;"+objectName+";";
	    	//System.out.print(vs.size());
	    	
	    		String cpu_usage = (String)vs.getJSONObject(0).get("metricValue");
	    		String mem_usage = (String)vs.getJSONObject(1).get("metricValue");
	    		String nic_byte_in = (String)vs.getJSONObject(2).get("metricValue");
	    		String nic_byte_out = (String)vs.getJSONObject(3).get("metricValue");
	    		String disk_io_in = (String)vs.getJSONObject(4).get("metricValue");
	    		String disk_io_out = (String)vs.getJSONObject(5).get("metricValue");
	    		cpu_usage = cpu_usage.length()>0?cpu_usage:"0";
	    		mem_usage = mem_usage.length()>0?mem_usage:"0";
	    		nic_byte_in = nic_byte_in.length()>0?nic_byte_in:"0";
	    		nic_byte_out = nic_byte_out.length()>0?nic_byte_out:"0";
	    		disk_io_in = disk_io_in.length()>0?disk_io_in:"0";
	    		disk_io_out = disk_io_out.length()>0?disk_io_out:"0";
	    		float disk_io_rate =(Float.parseFloat(disk_io_out) + Float.parseFloat(disk_io_in))*1000;
	    		float cpu_util = Float.parseFloat(cpu_usage)/100;
	    		float mem_util = Float.parseFloat(mem_usage)/100;
	    		String CPU_UTIL = re+"CPU_UTIL;GLOBAL;"+cpu_util+";"+objectName;
	    		String MEM_UTIL = re+"MEM_UTIL;GLOBAL;"+mem_util+";"+objectName;
	    		String NET_IN_BYTE_RATE = re+"NET_IN_BYTE_RATE;GLOBAL;"+nic_byte_in+";"+objectName;
	    		String NET_OUT_BYTE_RATE = re+"NET_OUT_BYTE_RATE;GLOBAL;"+nic_byte_out+";"+objectName;
	    		String DISK_TRANSFER_RATE = re+"DISK_TRANSFER_RATE;GLOBAL;"+disk_io_rate+";"+objectName;
	    		
	    		System.out.println(CPU_UTIL);
	    		System.out.println(MEM_UTIL);
	    		System.out.println(NET_IN_BYTE_RATE);
	    		System.out.println(NET_OUT_BYTE_RATE);
	    		System.out.println(DISK_TRANSFER_RATE);
	    		
	    	
	    	//System.out.println(values);
		}
	}
	
	//获取FC的md5口令密文
	public String getPasswd(String password){
		MessageDigest sha = null;
		try {
			sha = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		sha.update(password.getBytes());
		String text = new HexBinaryAdapter().marshal(sha.digest()).toLowerCase();
		return text;
	} 
}
