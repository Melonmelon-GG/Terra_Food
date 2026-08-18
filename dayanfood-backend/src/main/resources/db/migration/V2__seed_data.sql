INSERT INTO region (name, province, description) VALUES
('成都','四川','天府之国，川菜与市井小吃交相辉映。'),('潮汕','广东','山海相拥，讲究本味与时鲜。'),
('苏州','江苏','江南水乡，滋味清雅而不失丰润。'),('西安','陕西','古都长安，面食与胡汉风味汇聚。');
INSERT INTO food (name,region_id,summary,story,ingredients,image_url,heat) VALUES
('麻婆豆腐',1,'麻、辣、烫、香、酥、嫩、鲜、活，一碗烟火气十足的川味经典。','相传始于清同治年间成都万福桥边的小饭铺，因店主陈氏脸有麻点而得名。','豆腐、牛肉末、郫县豆瓣、花椒、蒜苗','https://images.unsplash.com/photo-1582450871972-ab5ca641643d?auto=format&fit=crop&w=1200&q=80',98),
('潮汕牛肉火锅',2,'清汤见真章，鲜切牛肉按部位涮出不同口感。','从街巷牛肉炉发展而来，刀工、部位和秒数共同定义一口鲜嫩。','黄牛肉、牛骨清汤、南姜、沙茶酱、芹菜','https://images.unsplash.com/photo-1563245372-f21724e3856d?auto=format&fit=crop&w=1200&q=80',92),
('松鼠桂鱼',3,'形似松鼠、外脆里嫩，以酸甜卤汁唤醒江南宴席。','精细花刀体现苏帮菜功夫，乾隆下江南的传说又为它添了传奇。','桂鱼、番茄酱、松子、米醋、白糖','https://images.unsplash.com/photo-1515003197210-e0cd71810b5f?auto=format&fit=crop&w=1200&q=80',86),
('肉夹馍',4,'腊汁肉酥烂醇香，白吉馍外酥里软。','肉夹于馍的古汉语表达流传至今，是关中人日常而扎实的一餐。','猪肉、白吉馍、桂皮、八角、冰糖','https://images.unsplash.com/photo-1565299507177-b0ac66763828?auto=format&fit=crop&w=1200&q=80',89);
