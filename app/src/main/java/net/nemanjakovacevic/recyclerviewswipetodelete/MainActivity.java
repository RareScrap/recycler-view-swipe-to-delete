package net.nemanjakovacevic.recyclerviewswipetodelete;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Простая активити с демонстрацией функции удаления элементов RecyclerView по свайпу
 */
public class MainActivity extends AppCompatActivity {
    /** Сам список */
    RecyclerView mRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        setUpRecyclerView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_undo_checkbox) {
            item.setChecked(!item.isChecked());
            ((TestAdapter)mRecyclerView.getAdapter()).setUndoOn(item.isChecked());
        }
        if (item.getItemId() == R.id.menu_item_add_5_items) {
            ((TestAdapter)mRecyclerView.getAdapter()).addItems(5);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Инициализирует "ядро" списка, а так же инициализируется анимации и свайпы
     */
    private void setUpRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(new TestAdapter());
        mRecyclerView.setHasFixedSize(true);
        setUpItemTouchHelper(); // Инициализация системы, ответственная за свайпы
        setUpAnimationDecoratorHelper(); // Инициализация системы, ответственная за анимации
    }

    /**
     * Ниже - стандартная реализация "свайпа для удаления" для библиотеки поддержки. Вы можете кастомизировать отрисовку в методе onChildDraw,
     * но все, что вы нарисуете, исчезнет как только свайп закончится, и пока действует анимация, двигающая элементы списка к их новым позициям,
     * фон будет видимым. Это редкий и, зачастую, желаемый эффект.
     * (с) Полный перевод комментария из головного репозиория
     */
    private void setUpItemTouchHelper() {
        /**
         * Колбэе для позволенных событий свайпо и перетаскиванй.
         */
        /* В конструкторе, 0 - направление, в которое элемент может быть перетащен (0 - ни в какое).
         * Второй аргумент - направление, к которое элемент может быть свайпнут */
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            // we want to cache these and not allocate anything repeatedly in the onChildDraw method
            // мы хотим кешировать это и не распределять ничего повторно в методе onChildDraw TODO: ЯННП
            Drawable background;
            Drawable xMark; // Тот самый "крестик" справа на фоне
            int xMarkMargin; // Отступы крестика

            /** Флаг того, что метод {@link #init()} был вызван */
            boolean initiated; // ф

            /** Вызывается, чтобы подготовить ресурсы к отображению */
            private void init() {
                background = new ColorDrawable(Color.RED);
                xMark = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_clear_24dp); // Получение ресурса "крестика"
                xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP); // Фильтр, которй делает крестик белым
                xMarkMargin = (int) MainActivity.this.getResources().getDimension(R.dimen.ic_clear_margin);
                initiated = true;
            }

            // Не определяет, т.к. нам не нужен drag & drop
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getAdapterPosition();
                TestAdapter testAdapter = (TestAdapter)recyclerView.getAdapter();

                // TODO: Это дело запрещает свайп фона с кнопкой undo?
                if (testAdapter.isUndoOn() && testAdapter.isPendingRemoval(position)) {
                    return 0;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                int swipedPosition = viewHolder.getAdapterPosition();
                TestAdapter adapter = (TestAdapter)mRecyclerView.getAdapter();
                boolean undoOn = adapter.isUndoOn();
                if (undoOn) {
                    adapter.pendingRemoval(swipedPosition); // TODO: Помещает в очередь к удалению?
                } else {
                    adapter.remove(swipedPosition);
                }
            }

            /**
             * <a href="https://developer.android.com/reference/android/support/v7/widget/helper/ItemTouchHelper.Callback.html#onChildDraw(android.graphics.Canvas,%20android.support.v7.widget.RecyclerView,%20android.support.v7.widget.RecyclerView.ViewHolder,%20float,%20float,%20int,%20boolean)"></a>
             *
             * Этот метод так же вызывается, когда элемент сдвинут, палец убран, но список плавно "задвигает" сдвинутый элемент.
             * При этом viewHolder.getAdapterPosition() дает -1
             *
             * @param c Канва, на которой RecyclerView рисует то, что ему скажут
             * @param recyclerView
             * @param viewHolder
             * @param dX Величина горизонтального смещения (отностительно "нормальной" позиции элемента), вызванного действием пользователя
             * @param dY Величина вертикального смещения (отностительно "нормальной" позиции элемента), вызванного действием пользователя
             * @param actionState Тип взаимодействия во View. Это может быть ACTION_STATE_DRAG или ACTION_STATE_SWIPE.
             * @param isCurrentlyActive True, когда анимация вызвана дейсвтиями юзера и False, когда анимация работает "сама по себе"
             */
            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;

                //by RareScrap
                //Log.i("isCurrentlyActive", String.valueOf(isCurrentlyActive));

                // not sure why, but this method get's called for viewholder that are already swiped away
                if (viewHolder.getAdapterPosition() == -1) {
                    //by RareScrap
                    //Log.i("AdapterPosition = -1", "YES");

                    // not interested in those
                    return;
                }

                /* Подготовить ресурсы к отображению. Если этого не сделать, один из ресурсов будет равен null и повлечет краш
                 * Вызывается лишь однажды */
                if (!initiated) {
                    //by RareScrap
                    //Log.i("init_in_onChildView", "we ready to start init()");

                    init();
                }

                // Рисует красный фон, на основании того, как дале пользователь "увел" свайпом элемент от его первоначальной позиции
                // ЭТИ СТРОКИ НЕ ОТВЕЧАЮТ ЗА ЗАПОЛНЕНИЕ ПРОСТРАНСТВА ЭЛЕМЕНТА КРАСНЫМ ФОНОМ ПОСЛЕ ТОГО, КАК О БЫЛ УДАЛЕН! Т.Е. ВО ВРЕМЯ "ЗАТЯГИВАНИЯ" СПИСКА!
                background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                background.draw(c); // Дать приказ о рисовании фона на канве

                // draw x mark
                /*
                getTop(), getBottom и все методы такого вида возвращают НЕ КООРДИНАТЫ, а расстояни (в пикселях) от своего родителя с заданной стороны.
                Т.е. itemView.getBottom() вернет растояние в пикселях от верха/лева (я про стороны экрана) в зависимости от get-функции
                (т.е. ну ясен пень getBottom не будет считаться относительно левого края экрана!)
                 */
                int itemHeight = itemView.getBottom() - itemView.getTop(); // Растояние в пикселях!
                int intrinsicWidth = xMark.getIntrinsicWidth(); // Слово "Intrinsic" можно просто отбросить
                int intrinsicHeight = xMark.getIntrinsicWidth();

                int xMarkLeft = itemView.getRight() - xMarkMargin - intrinsicWidth;
                int xMarkRight = itemView.getRight() - xMarkMargin;
                int xMarkTop = itemView.getTop() + (itemHeight - intrinsicHeight)/2;
                int xMarkBottom = xMarkTop + intrinsicHeight;

                // Определяет "квадратик" на вьюхе, где будет нарисован xMark
                xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom);

                xMark.draw(c);

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

        };
        ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        mItemTouchHelper.attachToRecyclerView(mRecyclerView);
    }

    /**
     * We're gonna setup another ItemDecorator that will draw the red background in the empty space while the items are animating to thier new positions
     * after an item is removed.
     */
    private void setUpAnimationDecoratorHelper() {
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {

            // we want to cache this and not allocate anything repeatedly in the onDraw method
            Drawable background;
            boolean initiated;

            private void init() {
                background = new ColorDrawable(Color.RED);
                initiated = true;
            }

            @Override
            public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {

                if (!initiated) {
                    init();
                }

                // only if animation is in progress
                if (parent.getItemAnimator().isRunning()) {

                    // some items might be animating down and some items might be animating up to close the gap left by the removed item
                    // this is not exclusive, both movement can be happening at the same time
                    // to reproduce this leave just enough items so the first one and the last one would be just a little off screen
                    // then remove one from the middle

                    /*
                    Некоторые элементы могуть быть анимированы вниз, а некоторые анимированы вверх, чтобы закрыть место, оставленно предварительно удаленным элементом.
                    Не исключено, что оба движения могут происходить одновременно.
                    Чтобы воспроизвести это, достаточно одновременно удалить два элемента (очень удобно, если поддерживает мультитач. Еще, это будет лучше заменто, если одновременно удалить два сосведих элемента).
                    TODO: Дальше в англ. комментах нихуя не понял
                     */

                    // find first child with translationY > 0
                    // and last one with translationY < 0
                    // we're after a rect that is not covered in recycler-view views at this point in time
                    View lastViewComingDown = null;
                    View firstViewComingUp = null;

                    // this is fixed
                    int left = 0;
                    int right = parent.getWidth();

                    // this we need to find out
                    int top = 0;
                    int bottom = 0;

                    // find relevant translating views
                    int childCount = parent.getLayoutManager().getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        View child = parent.getLayoutManager().getChildAt(i);
                        if (child.getTranslationY() < 0) {
                            // view is coming down
                            lastViewComingDown = child;
                        } else if (child.getTranslationY() > 0) {
                            // view is coming up
                            if (firstViewComingUp == null) {
                                firstViewComingUp = child;
                            }
                        }
                    }

                    if (lastViewComingDown != null && firstViewComingUp != null) {
                        // views are coming down AND going up to fill the void
                        top = lastViewComingDown.getBottom() + (int) lastViewComingDown.getTranslationY();
                        bottom = firstViewComingUp.getTop() + (int) firstViewComingUp.getTranslationY();
                    } else if (lastViewComingDown != null) {
                        // views are going down to fill the void
                        top = lastViewComingDown.getBottom() + (int) lastViewComingDown.getTranslationY();
                        bottom = lastViewComingDown.getBottom();
                    } else if (firstViewComingUp != null) {
                        // views are coming up to fill the void
                        top = firstViewComingUp.getTop();
                        bottom = firstViewComingUp.getTop() + (int) firstViewComingUp.getTranslationY();
                    }

                    background.setBounds(left, top, right, bottom);
                    background.draw(c);

                }
                super.onDraw(c, parent, state);
            }

        });
    }

    /**
     * RecyclerView adapter enabling undo on a swiped away item.
     */
    class TestAdapter extends RecyclerView.Adapter {

        private static final int PENDING_REMOVAL_TIMEOUT = 3000; // 3sec

        List<String> items;
        List<String> itemsPendingRemoval; // Элементы, ожидающие удаления
        int lastInsertedIndex; // so we can add some more items for testing purposes
        boolean undoOn; // is undo on, you can turn it on from the toolbar menu

        private Handler handler = new Handler(); // hanlder for running delayed runnables
        HashMap<String, Runnable> pendingRunnables = new HashMap<>(); // map of items to pending runnables, so we can cancel a removal if need be

        public TestAdapter() {
            items = new ArrayList<>();
            itemsPendingRemoval = new ArrayList<>();
            // let's generate some items
            lastInsertedIndex = 15;
            // this should give us a couple of screens worth
            for (int i=1; i<= lastInsertedIndex; i++) {
                items.add("Item " + i);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new TestViewHolder(parent);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TestViewHolder viewHolder = (TestViewHolder)holder;
            final String item = items.get(position);

            if (itemsPendingRemoval.contains(item)) {
                // we need to show the "undo" state of the row
                viewHolder.itemView.setBackgroundColor(Color.RED);
                viewHolder.titleTextView.setVisibility(View.GONE);
                viewHolder.undoButton.setVisibility(View.VISIBLE);
                viewHolder.undoButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // user wants to undo the removal, let's cancel the pending task
                        Runnable pendingRemovalRunnable = pendingRunnables.get(item);
                        pendingRunnables.remove(item);
                        if (pendingRemovalRunnable != null) handler.removeCallbacks(pendingRemovalRunnable);
                        itemsPendingRemoval.remove(item);
                        // this will rebind the row in "normal" state
                        notifyItemChanged(items.indexOf(item));
                    }
                });
            } else {
                // we need to show the "normal" state
                viewHolder.itemView.setBackgroundColor(Color.WHITE);
                viewHolder.titleTextView.setVisibility(View.VISIBLE);
                viewHolder.titleTextView.setText(item);
                viewHolder.undoButton.setVisibility(View.GONE);
                viewHolder.undoButton.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         *  Utility method to add some rows for testing purposes. You can add rows from the toolbar menu.
         */
        public void addItems(int howMany){
            if (howMany > 0) {
                for (int i = lastInsertedIndex + 1; i <= lastInsertedIndex + howMany; i++) {
                    items.add("Item " + i);
                    notifyItemInserted(items.size() - 1);
                }
                lastInsertedIndex = lastInsertedIndex + howMany;
            }
        }

        public void setUndoOn(boolean undoOn) {
            this.undoOn = undoOn;
        }

        public boolean isUndoOn() {
            return undoOn;
        }

        public void pendingRemoval(int position) {
            final String item = items.get(position);
            if (!itemsPendingRemoval.contains(item)) {
                itemsPendingRemoval.add(item);
                // this will redraw row in "undo" state
                notifyItemChanged(position);
                // let's create, store and post a runnable to remove the item
                Runnable pendingRemovalRunnable = new Runnable() {
                    @Override
                    public void run() {
                        remove(items.indexOf(item));
                    }
                };
                handler.postDelayed(pendingRemovalRunnable, PENDING_REMOVAL_TIMEOUT);
                pendingRunnables.put(item, pendingRemovalRunnable);
            }
        }

        public void remove(int position) {
            String item = items.get(position);
            if (itemsPendingRemoval.contains(item)) {
                itemsPendingRemoval.remove(item);
            }
            if (items.contains(item)) {
                items.remove(position);
                notifyItemRemoved(position);
            }
        }

        public boolean isPendingRemoval(int position) {
            String item = items.get(position);
            return itemsPendingRemoval.contains(item);
        }
    }

    /**
     * ViewHolder capable of presenting two states: "normal" and "undo" state.
     */
    static class TestViewHolder extends RecyclerView.ViewHolder {

        TextView titleTextView;
        Button undoButton;

        public TestViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_view, parent, false));
            titleTextView = (TextView) itemView.findViewById(R.id.title_text_view);
            undoButton = (Button) itemView.findViewById(R.id.undo_button);
        }

    }

}
