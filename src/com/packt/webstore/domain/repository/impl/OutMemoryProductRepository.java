package com.packt.webstore.domain.repository.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.packt.webstore.domain.Product;
import com.packt.webstore.domain.repository.ProductRepository;
import com.packt.webstore.exception.ProductNotFoundException;

@Repository
@Component(value="OutMemory")
public class OutMemoryProductRepository implements ProductRepository{

	private List<Product> listOfProducts = new ArrayList<Product>();
	
	public OutMemoryProductRepository(){
		
		Product iphone = new Product("P1234", "iPhone 5s", new BigDecimal(500));
		iphone.setDescription("Apple iPhone 5s smartphone with 4.00-inch 640x1136 display and 8-megapixel rear camera");
		iphone.setCategory("Smart-Phone");
		iphone.setManufacturer("Apple");
		iphone.setUnitsInStock(1000);
		iphone.setImageSource("P1234.jpg");
		
		Product laptop_dell = new Product("P1235","Dell Inspiron", new  BigDecimal(700));
		laptop_dell.setDescription("Dell Inspiron 14-inch Laptop (Black) with 3rd Generation Intel Core processors");
		laptop_dell.setCategory("Laptop");
		laptop_dell.setManufacturer("Dell"); 
		laptop_dell.setUnitsInStock(1000);
		laptop_dell.setImageSource("P1235.jpg");
		
		Product tablet_Nexus = new Product("P1236","Nexus 7", new BigDecimal(300));
		tablet_Nexus.setDescription("Google Nexus 7 is the lightest 7 inch tablet With a quad-core Qualcomm SnapdragonTM S4 Pro processor");
		tablet_Nexus.setCategory("Tablet");
		tablet_Nexus.setManufacturer("Google");
		tablet_Nexus.setUnitsInStock(1000);
		tablet_Nexus.setImageSource("P1236.jpg");
		
		Product galaxy_s5 = new Product("P1237","Galaxy S5", new BigDecimal(700));
		galaxy_s5.setDescription("Samsung galaxy S5 is the 5 inch 2.5k display smart phone with an octa-core Exynos processor.");
		galaxy_s5.setCategory("Smart-Phone");
		galaxy_s5.setManufacturer("Samsung");
		galaxy_s5.setUnitsInStock(2000);
		galaxy_s5.setImageSource("P1237.jpg");
		
		Product tablet_Nexus9 = new Product("P1238","Nexus 9", new BigDecimal(700));
		tablet_Nexus9.setDescription("Nexus 9 is latest tablet from google running lollipop with 8.9 inch IPS LCD Display and NVIDIA Tegra K1 Processor.");
		tablet_Nexus9.setCategory("Tablet");
		tablet_Nexus9.setManufacturer("Google");
		tablet_Nexus9.setUnitsInStock(1500);
		tablet_Nexus9.setImageSource("P1238.jpg");
		
		listOfProducts.add(iphone);
		listOfProducts.add(laptop_dell);
		listOfProducts.add(tablet_Nexus);
		listOfProducts.add(galaxy_s5);
		listOfProducts.add(tablet_Nexus9);
	}
	
	@Override
	public List<Product> getAllProducts(){
		return listOfProducts;
	}
	
	@Override
	public Product getProductById(String productid) {
		Product productById = null;
		for(Product product: listOfProducts){
			if(product != null && product.getProductId() != null && product.getProductId().equals(productid)){
				productById = product;
				break;
			}
		}
		if(productById == null){
			throw new ProductNotFoundException(productid);
		}
		return productById;
	}
	
	@Override
	public List<Product> getProductsByCategory(String category) {
		List<Product> productsByCategory = new ArrayList<Product>();
		for(Product product: listOfProducts){
			if(category.equalsIgnoreCase(product.getCategory()))
				productsByCategory.add(product);
		}
		
		return productsByCategory;
	}

	
	@Override
	public Set<Product> getProductsByFiler( Map<String, List<String>> filterParams) {

		Set<Product> productsByBrand = new HashSet<Product>();
		Set<Product> productsByCategory = new HashSet<Product>();
		
		Set<String> criterias = filterParams.keySet();
		
		if(criterias.contains("brand")){
			for(String brandName: filterParams.get("brand")){
				for(Product product: listOfProducts){
					if(brandName.equalsIgnoreCase(product.getManufacturer())){
						productsByBrand.add(product);
					}
					
				}
			}
		}
		
		if(criterias.contains("category")){
			for(String categoryName: filterParams.get("category")){
				productsByCategory.addAll(this.getProductsByCategory(categoryName));
			}
		}
		
		productsByCategory.retainAll(productsByBrand);
			
		return productsByCategory;
	}

	@Override
	public void addProduct(Product product) {
		listOfProducts.add(product);
	}
}
