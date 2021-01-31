package com.szu.spring.myRegisterEditor;

public class Address {
	private String province;
	private String city;
	private String town;
	private String community;
	private String building;


	public String getCommunity() {
		return community;
	}

	public void setCommunity(String community) {
		this.community = community;
	}



	public String getProvince() {
		return province;
	}

	public void setProvince(String province) {
		this.province = province;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getTown() {
		return town;
	}

	public void setTown(String town) {
		this.town = town;
	}

	public String getBuilding() {
		return building;
	}

	public void setBuilding(String building) {
		this.building = building;
	}

	@Override
	public String toString() {
		return "Address{" +
				"province='" + province + '\'' +
				", city='" + city + '\'' +
				", town='" + town + '\'' +
				", community='" + community + '\'' +
				", building='" + building + '\'' +
				'}';
	}
}
