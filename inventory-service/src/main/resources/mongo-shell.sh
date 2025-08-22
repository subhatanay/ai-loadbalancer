// Use mongo shell: mongosh
use inventorydb

db.products.insertMany([
  { "_id": "product-101", "name": "Laptop Pro", "description": "A powerful laptop", "price": 1200.0, "stockQuantity": 150, "createdAt": new Date(), "updatedAt": new Date() },
  { "_id": "product-102", "name": "Wireless Mouse", "description": "Ergonomic wireless mouse", "price": 45.50, "stockQuantity": 500, "createdAt": new Date(), "updatedAt": new Date() },
  { "_id": "product-103", "name": "Mechanical Keyboard", "description": "RGB mechanical keyboard", "price": 95.0, "stockQuantity": 250, "createdAt": new Date(), "updatedAt": new Date() }
]);