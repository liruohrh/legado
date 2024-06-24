- 自定义阅读视图：`io.legado.app.ui.book.read.page.ReadView`
	- 有prePage、curPage、nextPage
		- 从`io.legado.app.ui.book.read.page.provider.TextPageFactory`中获取
	-  upContent：更新以上3个Page
	- `io.legado.app.model.ReadBook`：当前阅读书籍、章节、页面等信息
		- loadContent：
			- `io.legado.app.help.book.BookHelp#getContent：本地获取`
			- download：爬虫
				- `io.legado.app.model.CacheBook#getOrCreate(BookSource, Book)`
				- `io.legado.app.model.CacheBook.CacheBookModel#download(CoroutineScope, BookChapter, boolean)`
					- `io.legado.app.model.webBook.WebBook#getContent`
						- 异步解析规则、根据规则爬取数据，保存数据（文字，图片）
					- `io.legado.app.model.CacheBook.CacheBookModel#downloadFinish`
						- `io.legado.app.model.ReadBook#contentLoadFinish`
							- 							`io.legado.app.ui.book.read.page.provider.ChapterProvider#getTextChapter`
								- 获取TextChapter，且创建所有TextPage、TextLine、BaseColumn
							- 异步处理数据，且初始化ReadBook，且调用回调对象
								- `io.legado.app.model.ReadBook.callBack`
								- 就是`io.legado.app.ui.book.read.ReadBookActivity`
								- 即进行把"加载中"页面更新为用当前获取的数据创建的页面
- 一页视图`io.legado.app.ui.book.read.page.PageView`
	- 涉及数据对象：
		- `io.legado.app.ui.book.read.page.entities.TextChapter`
		- `io.legado.app.ui.book.read.page.entities.TextPage`
		- `io.legado.app.ui.book.read.page.entities.TextLine`
		- `io.legado.app.ui.book.read.page.entities.column.BaseColumn`
		- .....
	- 滚动页面只需要一页，即curPage，每次都是更新curPage的content
- 内容部分`io.legado.app.ui.book.read.page.ContentTextView`
	- 在`View#onDraw(android.graphics.Canvas)`方法中使用Canvas进行drawPage，对TextPage中每个TextLine的columns进行draw
	- 重新设置：setContent，最后invalidate使当前失效，重新draw



# 优化：图片异步加载（第一次获取时）
- 写完才发现，截至2024/6/24，官方已经做出像样的异步加载网络图片了
  - 区别：
    - 人家：重写了UI，使得按顺序一张一张地加载图片
    - 我：仍然是加载全部，但是如果还未下载完的显示加载错误图片，需要等待下载完再触摸一下更新UI
- 把`io.legado.app.model.CacheBook.CacheBookModel#download(kotlinx.coroutines.CoroutineScope, io.legado.app.data.entities.BookChapter, boolean)`中的变成异步`WebBook.getContent`中的最后一步`BookHelp.saveContent`与downloadFinish的`ReadBook#contentLoadFinish`变成异步，不要保存、下载完数据后才contentLoadFinish
	- `BookHelp.saveContent`只针对saveImages进行优化
- 方法：只修改一处即可，让`io.legado.app.help.book.BookHelp#saveImages`变成异步的，而不是只是有一个异步流（需要等待该异步流完成才能继续执行）
	- `io.legado.app.ui.book.read.page.provider.ChapterProvider#getTextChapter`需要获取图片的size，如果没有该图片会进行下载，因此添加参数anyImageSize=true，获取任意一张的大小
- 问题
	1. get null 怎么办？
		- 根据`io.legado.app.ui.book.read.page.provider.TextPageFactory#getCurPage`，可知是一个"加载中"页面
		- 因此不太担心划太多超时当前拥有的范围
	2. 中途发生错误 怎么办，需要额外逻辑吗？完成下载呢？
		- 照抄`io.legado.app.model.CacheBook.CacheBookModel#download(kotlinx.coroutines.CoroutineScope, io.legado.app.data.entities.BookChapter, boolean)`
			- 其中
				- `onFinally`  `postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)`
					- 好像是更新缓存页面的状态，但是好像没改什么
				- `onError`：下载失败在下载那里有处理异常，不需要
				- `onCancel`：由于在`onSuccess`就已经`onDownloadSet.remove`，只是还有`if (!isStopped) waitDownloadSet.add(index)`，不过感觉可以忽视，不用管
		- 因此不需要额外逻辑
	1. 退出阅读界面后仍然在下载怎么办？
		1. 解决方法：异步的`io.legado.app.help.book.BookHelp#saveImages`使用`ReadBook.downloadScope`（从源头`loadContent`看见给的，当退出阅读界面时，会使用该scope进行取消，从而取消下载）
	2. 在阅读界面退出重新进入，不会继续下载或者重新下载图片
		1. 感觉理所当然不应该重新下载，而是需要刷新，刷新不会重新下载所有，而是下载未下载的（官方本来就是这样写的）