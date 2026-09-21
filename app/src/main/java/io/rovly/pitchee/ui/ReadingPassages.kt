package io.rovly.pitchee.ui

internal data class ReadingPassage(
    val titleZh: String,
    val titleEn: String,
    val textZh: String,
    val textEn: String,
) {
    fun title(languageCode: String): String =
        if (languageCode.startsWith("en")) titleEn else titleZh

    fun text(languageCode: String): String =
        if (languageCode.startsWith("en")) textEn else textZh

}

internal val readingPassages = listOf(
    ReadingPassage(
        titleZh = "北风与太阳",
        titleEn = "The North Wind and the Sun",
        textZh = "北风和太阳争论谁更有力量。路上来了一位行人，他们约定，谁能让行人脱下外套，谁就赢。北风用力吹，行人反而把外套裹得更紧。太阳温暖地照着，行人渐渐感到热，最后主动脱下了外套。",
        textEn = "The North Wind and the Sun argued about who was stronger. A traveler came down the road, so they agreed that whoever made him remove his coat would win. The wind blew hard, but the traveler held his coat tighter. The sun shone warmly until the traveler took off the coat himself.",
    ),
    ReadingPassage(
        titleZh = "乌鸦喝水",
        titleEn = "The Crow and the Pitcher",
        textZh = "一只乌鸦口渴了，到处找水喝。它看见一个瓶子，瓶子里有水，可是水位太低，乌鸦够不着。乌鸦衔来一颗颗小石子放进瓶里。瓶子里的水渐渐升高，乌鸦终于喝到了水。",
        textEn = "A thirsty crow searched everywhere for water. It found a pitcher with water inside, but the water was too low for its beak to reach. The crow dropped one small stone after another into the pitcher. The water rose slowly, and at last the crow could drink.",
    ),
    ReadingPassage(
        titleZh = "小马过河",
        titleEn = "The Little Horse Crosses the River",
        textZh = "小马要把半袋麦子送到磨坊。一条小河挡住了路。牛说河水很浅，松鼠说河水很深。小马回家问妈妈。妈妈让它自己去试一试。小马小心地走进河里，发现河水既不像牛说的那么浅，也不像松鼠说的那么深。",
        textEn = "A little horse had to carry half a bag of wheat to the mill. A river blocked the road. The ox said it was shallow, while the squirrel said it was deep. The horse asked its mother, who told it to try for itself. The horse stepped in carefully and learned that the river was neither as shallow nor as deep as the others had said.",
    ),
    ReadingPassage(
        titleZh = "龟兔赛跑",
        titleEn = "The Tortoise and the Hare",
        textZh = "兔子嘲笑乌龟走得太慢，乌龟便提出比赛。兔子跑得很快，很快就把乌龟甩在后面。它觉得胜券在握，就在路边睡着了。乌龟一步一步向前走，从兔子身边经过，最后先到达终点。",
        textEn = "The hare laughed at the tortoise for being slow, so the tortoise challenged him to a race. The hare ran far ahead and, certain of victory, stopped for a nap. The tortoise kept walking steadily, passed the sleeping hare, and reached the finish line first.",
    ),
    ReadingPassage(
        titleZh = "狐狸和葡萄",
        titleEn = "The Fox and the Grapes",
        textZh = "一只饥饿的狐狸看见架子上挂着一串葡萄。它跳了好几次，始终够不着。狐狸最后转身离开，嘴里说：“这些葡萄一定是酸的。”有些事情做不到时，人们常常会说它不值得。",
        textEn = "A hungry fox saw a bunch of grapes hanging from a vine. He jumped again and again but could not reach them. At last he walked away and said, “Those grapes are probably sour anyway.” People often call something worthless when they cannot have it.",
    ),
    ReadingPassage(
        titleZh = "蚂蚁和蚱蜢",
        titleEn = "The Ant and the Grasshopper",
        textZh = "夏天里，蚂蚁忙着搬运粮食，蚱蜢却每天唱歌玩耍。蚱蜢笑蚂蚁太辛苦。冬天来了，蚱蜢找不到食物，只能向蚂蚁求助。蚂蚁提醒它，夏天准备，冬天才不会挨饿。",
        textEn = "Through the summer, the ants stored food while the grasshopper sang and played. The grasshopper laughed at them for working so hard. When winter came, he had nothing to eat and asked the ants for help. They reminded him that preparing in summer prevents hunger in winter.",
    ),
    ReadingPassage(
        titleZh = "农夫与蛇",
        titleEn = "The Farmer and the Snake",
        textZh = "寒冷的冬天，农夫在路边发现一条冻僵的蛇。他把蛇放进怀里取暖。蛇醒来后恢复了本性，咬伤了农夫。农夫后悔地说，他不该同情本性难移的恶人。",
        textEn = "On a cold winter day, a farmer found a frozen snake by the road and carried it inside his coat. When the snake revived, it bit him. The farmer regretted helping a creature whose nature had not changed.",
    ),
    ReadingPassage(
        titleZh = "狮子和老鼠",
        titleEn = "The Lion and the Mouse",
        textZh = "狮子抓住了一只小老鼠。老鼠请求狮子放它走，并答应以后一定报答。狮子觉得它太小，不会有什么用处，但还是放了它。后来狮子落入猎人的网中，老鼠咬断绳子，救出了狮子。",
        textEn = "A lion caught a tiny mouse. The mouse begged to be released and promised to repay him. The lion thought such a small creature could never help, but let it go. Later, the mouse gnawed through a hunter’s net and set the lion free.",
    ),
    ReadingPassage(
        titleZh = "城市老鼠和乡下老鼠",
        titleEn = "The Town Mouse and the Country Mouse",
        textZh = "乡下老鼠请城市老鼠吃简单的粮食。城市老鼠嫌这里清苦，邀请它去城里享受美食。它们在城里刚坐下，猫和狗就接连出现。乡下老鼠只好逃回家，觉得安稳比丰盛更重要。",
        textEn = "A country mouse invited a town mouse to a simple meal. The town mouse called it plain and invited him to the city. They had barely started eating when cats and dogs appeared. The country mouse ran home, deciding that peace mattered more than luxury.",
    ),
    ReadingPassage(
        titleZh = "狐狸和乌鸦",
        titleEn = "The Fox and the Crow",
        textZh = "乌鸦叼着一块肉停在树上。狐狸很想得到它，就不断夸乌鸦的羽毛漂亮、歌声动听。乌鸦得意地张嘴唱歌，肉掉了下来。狐狸叼起肉说，下次不要轻易相信奉承。",
        textEn = "A crow sat in a tree with a piece of meat. Wanting it, the fox praised the crow’s feathers and voice. Flattered, the crow opened its beak to sing, and the meat fell. The fox picked it up and said not to trust flattery too easily.",
    ),
    ReadingPassage(
        titleZh = "两个旅人与熊",
        titleEn = "The Two Travelers and the Bear",
        textZh = "两个旅人一起赶路，突然遇到一只熊。一个人迅速爬上树，另一个人来不及逃，只好躺下装死。熊闻了闻他的脸，以为他没有呼吸，便转身离开。树上的人下来后问熊说了什么。地上的人回答：“它说，危险时抛下朋友的人不可靠。”",
        textEn = "Two travelers met a bear on the road. One climbed a tree, while the other fell to the ground and pretended to be dead. The bear sniffed his face, believed he was not breathing, and walked away. Later, the man in the tree asked what the bear had said. “It said not to trust a friend who abandons you in danger.”",
    ),
)
