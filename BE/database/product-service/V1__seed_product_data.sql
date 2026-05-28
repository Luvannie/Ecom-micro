-- Product Service Seed Data
-- Run after V1__create_catalog_tables.sql

TRUNCATE categories CASCADE;
TRUNCATE products CASCADE;

INSERT INTO categories (id, name, slug, active, created_at, updated_at) VALUES
('ca010101-0101-0101-0101-010101010101', 'Fast Food', 'fast-food', true, NOW(), NOW()),
('ca020202-0202-0202-0202-020202020202', 'Vietnamese', 'vietnamese', true, NOW(), NOW()),
('ca030303-0303-0303-0303-030303030303', 'Chinese', 'chinese', true, NOW(), NOW()),
('ca040404-0404-0404-0404-040404040404', 'Japanese', 'japanese', true, NOW(), NOW()),
('ca050505-0505-0505-0505-050505050505', 'Desserts', 'desserts', true, NOW(), NOW()),
('ca060606-0606-0606-0606-060606060606', 'Beverages', 'beverages', true, NOW(), NOW());

INSERT INTO products (id, category_id, name, slug, description, price, image_url, active, promotion_tag, created_at, updated_at) VALUES
-- Fast Food
('1a111111-1111-1111-1111-111111111111', 'ca010101-0101-0101-0101-010101010101', 'Classic Burger', 'classic-burger', 'Juicy beef patty with fresh lettuce, tomato, and special sauce', 8.99, 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400', true, 'Best Seller', NOW(), NOW()),
('1b222222-2222-2222-2222-222222222222', 'ca010101-0101-0101-0101-010101010101', 'Crispy Chicken Wings', 'crispy-chicken-wings', '12 pieces of golden crispy chicken wings with dipping sauce', 12.99, 'https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?w=400', true, NULL, NOW(), NOW()),
('1c333333-3333-3333-3333-333333333333', 'ca010101-0101-0101-0101-010101010101', 'French Fries Large', 'french-fries-large', 'Large portion of crispy golden french fries', 4.99, 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=400', true, NULL, NOW(), NOW()),
('1d444444-4444-4444-4444-444444444444', 'ca010101-0101-0101-0101-010101010101', 'Double Cheeseburger', 'double-cheeseburger', 'Two beef patties with melted cheese and pickles', 11.99, 'https://images.unsplash.com/photo-1553979459-d2229ba74320?w=400', true, '50% Off', NOW(), NOW()),

-- Vietnamese
('2a555555-5555-5555-5555-555555555555', 'ca020202-0202-0202-0202-020202020202', 'Pho Bo', 'pho-bo', 'Traditional Vietnamese beef noodle soup with herbs', 9.99, 'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=400', true, NULL, NOW(), NOW()),
('2b666666-6666-6666-6666-666666666666', 'ca020202-0202-0202-0202-020202020202', 'Banh Mi', 'banh-mi', 'Vietnamese sandwich with grilled pork and pickled vegetables', 6.99, 'https://images.unsplash.com/photo-1600688640154-9619e002df30?w=400', true, 'Popular', NOW(), NOW()),
('2c777777-7777-7777-7777-777777777777', 'ca020202-0202-0202-0202-020202020202', 'Com Tam', 'com-tam', 'Broken rice with grilled pork chop and egg', 8.49, 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400', true, NULL, NOW(), NOW()),
('2d888888-8888-8888-8888-888888888888', 'ca020202-0202-0202-0202-020202020202', 'Bun Cha', 'bun-cha', 'Grilled pork patties with vermicelli and fresh herbs', 8.99, 'https://images.unsplash.com/photo-1627308595229-7830a5cb8c18?w=400', true, NULL, NOW(), NOW()),

-- Chinese
('3a999999-9999-9999-9999-999999999999', 'ca030303-0303-0303-0303-030303030303', 'Kung Pao Chicken', 'kung-pao-chicken', 'Spicy stir-fried chicken with peanuts and vegetables', 10.99, 'https://images.unsplash.com/photo-1525755662778-989d0524087e?w=400', true, NULL, NOW(), NOW()),
('3b000000-0000-0000-0000-000000000000', 'ca030303-0303-0303-0303-030303030303', 'Sweet and Sour Pork', 'sweet-sour-pork', 'Crispy pork in tangy sweet and sour sauce', 11.49, 'https://images.unsplash.com/photo-1569058242567-93de6f36f8eb?w=400', true, NULL, NOW(), NOW()),
('3c111111-1111-1111-1111-111111111112', 'ca030303-0303-0303-0303-030303030303', 'Fried Rice', 'fried-rice', 'Classic egg fried rice with vegetables', 7.99, 'https://images.unsplash.com/photo-1603133872878-684f208fb84b?w=400', true, NULL, NOW(), NOW()),
('3d222222-2222-2222-2222-222222222223', 'ca030303-0303-0303-0303-030303030303', 'Dim Sum Set', 'dim-sum-set', 'Assorted 12 pieces of steamed dim sum', 14.99, 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400', true, NULL, NOW(), NOW()),

-- Japanese
('4a333333-3333-3333-3333-333333333334', 'ca040404-0404-0404-0404-040404040404', 'Sushi Combo', 'sushi-combo', 'Premium 20 pieces assorted sushi', 24.99, 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400', true, 'Premium', NOW(), NOW()),
('4b444444-4444-4444-4444-444444444445', 'ca040404-0404-0404-0404-040404040404', 'Ramen', 'ramen', 'Authentic Japanese ramen with chashu pork', 13.99, 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400', true, NULL, NOW(), NOW()),
('4c555555-5555-5555-5555-555555555556', 'ca040404-0404-0404-0404-040404040404', 'Tempura Udon', 'tempura-udon', 'Thick udon noodles in hot soup with shrimp tempura', 12.99, 'https://images.unsplash.com/photo-1617093727343-374698b1b08d?w=400', true, NULL, NOW(), NOW()),
('4d666666-6666-6666-6666-666666666667', 'ca040404-0404-0404-0404-040404040404', 'Teriyaki Bento', 'teriyaki-bento', 'Grilled chicken teriyaki with rice and side dishes', 15.99, 'https://images.unsplash.com/photo-1561361058-c24ee3e8e1b3?w=400', true, NULL, NOW(), NOW()),

-- Desserts
('5a777777-7777-7777-7777-777777777778', 'ca050505-0505-0505-0505-050505050505', 'Chocolate Cake', 'chocolate-cake', 'Rich chocolate layer cake with frosting', 6.99, 'https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=400', true, NULL, NOW(), NOW()),
('5b888888-8888-8888-8888-888888888889', 'ca050505-0505-0505-0505-050505050505', 'Cheesecake', 'cheesecake', 'Creamy New York style cheesecake', 7.49, 'https://images.unsplash.com/photo-1565958011703-44f9829ba187?w=400', true, NULL, NOW(), NOW()),
('5c999999-9999-9999-9999-999999999990', 'ca050505-0505-0505-0505-050505050505', 'Ice Cream Sundae', 'ice-cream-sundae', '3 scoops with chocolate sauce and whipped cream', 5.99, 'https://images.unsplash.com/photo-1563805042-7684c019a59f?w=400', true, NULL, NOW(), NOW()),

-- Beverages
('6a000000-0000-0000-0000-000000000001', 'ca060606-0606-0606-0606-060606060606', 'Fresh Orange Juice', 'fresh-orange-juice', 'Freshly squeezed orange juice 500ml', 4.49, 'https://images.unsplash.com/photo-1621506289937-a8e4df240a08?w=400', true, NULL, NOW(), NOW()),
('6b111111-1111-1111-1111-111111111102', 'ca060606-0606-0606-0606-060606060606', 'Bubble Tea', 'bubble-tea', 'Classic milk tea with tapioca pearls', 5.49, 'https://images.unsplash.com/photo-1558857563-b371033873b8?w=400', true, NULL, NOW(), NOW()),
('6c222222-2222-2222-2222-222222222203', 'ca060606-0606-0606-0606-060606060606', 'Coconut Water', 'coconut-water', 'Fresh young coconut water 400ml', 3.99, 'https://images.unsplash.com/photo-1536657464919-8925381948ee?w=400', true, NULL, NOW(), NOW());